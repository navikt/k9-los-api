package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class K9PunsjTilLosAdapterTjeneste(
    private val k9PunsjEventRepository: K9PunsjEventRepository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val transactionalManager: TransactionalManager,
    private val pepCacheService: PepCacheService,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) {
    private val log: Logger = LoggerFactory.getLogger(K9PunsjTilLosAdapterTjeneste::class.java)

    @WithSpan
    fun spillAvDirtyBehandlingProsessEventer() {
        log.info("Starter avspilling av BehandlingProsessEventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val behandlingsIder = k9PunsjEventRepository.hentAlleDirtyEventIder()
        log.info("Fant ${behandlingsIder.size} behandlinger")

        var behandlingTeller: Long = 0
        var eventTeller: Long = 0
        behandlingsIder.forEach { uuid ->
            eventTeller = oppdaterOppgaveForEksternId(uuid, eventTeller)
            behandlingTeller++
            loggFremgangForHver100(behandlingTeller, "Forsert $behandlingTeller behandlinger")
        }

        val (antallAlle, antallAktive) = oppgaveV3Tjeneste.tellAntall()
        val tidHeleKjøringen = System.currentTimeMillis() - tidKjøringStartet
        log.info("Antall oppgaver etter kjøring: $antallAlle, antall aktive: $antallAktive, antall nye eventer: $eventTeller fordelt på $behandlingTeller behandlinger.")
        if (eventTeller > 0) {
            log.info("Gjennomsnittstid pr behandling: ${tidHeleKjøringen / behandlingTeller}ms, Gjennsomsnittstid pr event: ${tidHeleKjøringen / eventTeller}ms")
        }
        log.info("Avspilling av BehandlingProsessEventer ferdig")
    }

    @WithSpan
    fun oppdaterOppgaveForEksternId(@SpanAttribute uuid: UUID, eventTellerInn: Long = 0): Long {
        var eventTeller = eventTellerInn
        var forrigeOppgaveversjon: OppgaveV3? = null

        transactionalManager.transaction { tx ->
            val punsjEventer = k9PunsjEventRepository.hentMedLås(tx, uuid)
            for (event in punsjEventer.eventer) {
                val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(event, forrigeOppgaveversjon)
                val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                if (oppgave != null) {
                    pepCacheService.oppdater(tx, oppgave.kildeområde, oppgave.eksternId)

                    annullerReservasjonHvisPåVentEllerAvsluttet(oppgave, tx)
                    // Flere tilfeller som skal håndteres her?

                    eventTeller++
                    forrigeOppgaveversjon = oppgave
                } else {
                    forrigeOppgaveversjon = oppgaveV3Tjeneste.hentOppgaveversjon("K9", oppgaveDto.id, oppgaveDto.versjon, tx)
                }
            }

            runBlocking {
                køpåvirkendeHendelseChannel.send(
                    OppgaveHendelseMottatt(
                        Fagsystem.PUNSJ,
                        EksternOppgaveId("K9", uuid.toString())
                    )
                )
            }

            k9PunsjEventRepository.fjernDirty(uuid, tx)
        }

        return eventTeller
    }

    private fun annullerReservasjonHvisPåVentEllerAvsluttet(
        oppgave: OppgaveV3,
        tx: TransactionalSession
    ) {
        if (oppgave.status == Oppgavestatus.LUKKET || oppgave.status == Oppgavestatus.VENTER) {
            reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(
                oppgave.reservasjonsnøkkel,
                "Maskinelt annullert reservasjon, siden oppgave på reservasjonen er avsluttet eller på vent",
                null,
                tx
            )
        }
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }
}
