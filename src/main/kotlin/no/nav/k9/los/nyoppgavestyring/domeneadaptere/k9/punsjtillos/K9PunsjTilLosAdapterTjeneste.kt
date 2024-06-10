package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.punsjtillos

import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.PunsjEventK9Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeTjeneste
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.concurrent.thread

class K9PunsjTilLosAdapterTjeneste(
    private val eventRepository: PunsjEventK9Repository,
    private val oppgavetypeTjeneste: OppgavetypeTjeneste,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val oppgaveRepository: OppgaveRepository,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val config: Configuration,
    private val transactionalManager: TransactionalManager,
) {
    private val log: Logger = LoggerFactory.getLogger(K9PunsjTilLosAdapterTjeneste::class.java)
    private val TRÅDNAVN = "k9-punsj-til-los"

    fun kjør(kjørSetup: Boolean = false, kjørUmiddelbart: Boolean = false) {
        if (config.nyOppgavestyringAktivert()) {
            thread(
                start = true,
                isDaemon = true,
                name = TRÅDNAVN
            ) {
                spillAvBehandlingProsessEventer()
            }
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    private fun spillAvBehandlingProsessEventer() {
        log.info("Starter avspilling av BehandlingProsessEventer")
        val tidKjøringStartet = System.currentTimeMillis()

        val behandlingsIder = eventRepository.hentAlleDirtyEventIder()
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

    fun oppdaterOppgaveForEksternId(uuid: UUID, eventTellerInn: Long): Long {
        var eventTeller = eventTellerInn
        var forrigeOppgaveversjon: OppgaveV3? = null

        transactionalManager.transaction { tx ->
            val punsjEventer = eventRepository.hentMedLås(tx, uuid)
            for (event in punsjEventer.eventer) {
                val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(event, forrigeOppgaveversjon)
                val oppgave = oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)

                if (oppgave != null) {
                    //annullerReservasjoner hvis oppgave avsluttet.. andre tilfeller?
                    eventTeller++
                }

                forrigeOppgaveversjon = oppgave
            }
            eventRepository.fjernDirty(uuid, tx)
        }

        return eventTeller
    }
}
