package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.klage.typer.AktørId
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.beriker.K9KlageBerikerInterfaceKludge
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

class K9KlageTilLosAdapterTjeneste(
    private val k9KlageEventRepository: K9KlageEventRepository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val transactionalManager: TransactionalManager,
    private val k9klageBeriker: K9KlageBerikerInterfaceKludge,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>
) {

    private val log: Logger = LoggerFactory.getLogger(K9KlageTilLosAdapterTjeneste::class.java)

    @WithSpan
    fun spillAvDirtyBehandlingProsessEventer() {
        log.info("Starter avspilling av BehandlingProsessEventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val behandlingsIder = k9KlageEventRepository.hentAlleDirtyEventIder()
        log.info("Fant ${behandlingsIder.size} behandlinger")

        var behandlingTeller: Long = 0
        var eventTeller: Long = 0
        behandlingsIder.forEach { uuid ->
            eventTeller = oppdaterOppgaveForBehandlingUuid(uuid, eventTeller)
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

    fun oppdaterOppgaveForBehandlingUuid(uuid: UUID) {
        oppdaterOppgaveForBehandlingUuid(uuid, 0L)
    }

    @WithSpan
    private fun oppdaterOppgaveForBehandlingUuid(@SpanAttribute uuid: UUID, eventTellerInn: Long): Long {
        var eventTeller = eventTellerInn
        var forrigeOppgave: OppgaveV3? = null
        transactionalManager.transaction { tx ->
            val behandlingProsessEventer = k9KlageEventRepository.hentMedLås(tx, uuid).eventer
            behandlingProsessEventer.forEach { event ->
                val losOpplysningerSomManglerIKlageDto =
                    event.påklagdBehandlingId?.let { k9klageBeriker.hentFraK9Sak(it) }

                val eventBeriket =
                    event.copy(
                        påklagdBehandlingType = event.påklagdBehandlingType ?:
                        event.påklagdBehandlingId?.let {
                            k9klageBeriker.hentFraK9Klage(event.eksternId)?.påklagdBehandlingType
                        },
                        pleietrengendeAktørId = losOpplysningerSomManglerIKlageDto?.pleietrengendeAktørId?.aktørId?.let { AktørId(it.toLong()) },
                        utenlandstilsnitt = losOpplysningerSomManglerIKlageDto?.isUtenlandstilsnitt
                        )

                val oppgaveDto =
                    KlageEventTilOppgaveMapper.lagOppgaveDto(eventBeriket, forrigeOppgave)

                val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                oppgave?.let {
                    eventTeller++
                    loggFremgangForHver100(eventTeller, "Prosessert $eventTeller eventer")
                }
                forrigeOppgave = oppgave
            }
            forrigeOppgave = null

            runBlocking {
                køpåvirkendeHendelseChannel.send(OppgaveHendelseMottatt(Fagsystem.KLAGE, EksternOppgaveId("K9", uuid.toString())))
            }

            k9KlageEventRepository.fjernDirty(uuid, tx)
        }
        return eventTeller
    }

    private fun loggFremgangForHver100(teller: Long, tekst: String) {
        if (teller.mod(100) == 0) {
            log.info(tekst)
        }
    }
}
