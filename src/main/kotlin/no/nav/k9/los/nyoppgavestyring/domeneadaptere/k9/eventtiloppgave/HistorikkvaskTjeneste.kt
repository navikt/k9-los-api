package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.HistorikkvaskBestilling
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.measureTime

class HistorikkvaskTjeneste(
    private val eventRepository: EventRepository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val transactionalManager: TransactionalManager,
    private val eventTilOppgaveAdapter: EventTilOppgaveAdapter,
) {
    private val log: Logger = LoggerFactory.getLogger(HistorikkvaskTjeneste::class.java)

    companion object {
        private const val BATCH_STORRELSE = 2000
    }

    fun kjørHistorikkvask() {
        val antallBestillinger = eventRepository.hentAntallHistorikkvaskbestillinger()
        if (antallBestillinger == 0L) {
            return
        }

        log.info("Fant totalt $antallBestillinger historikkvaskbestillinger")
        // Cursor over event_nokkel_id, slik at en bestilling som feiler ikke fanger
        // den ytre løkka i en evig retry innenfor samme kjøring. Bestillinger som lykkes
        // blir uansett slettet fra event_historikkvask_bestilt.
        var sisteSetteEventNokkelId = 0L
        var vasketeller = 0L
        val tidsbruk = measureTime {
            while (true) {
                val historikkvaskbestillinger =
                    eventRepository.hentAlleHistorikkvaskbestillinger(
                        antall = BATCH_STORRELSE,
                        etterEventNokkelId = sisteSetteEventNokkelId,
                    )
                if (historikkvaskbestillinger.isEmpty()) break

                log.info("Starter vaskeiterasjon på ${historikkvaskbestillinger.size} oppgaver")
                vasketeller += vaskBestillinger(historikkvaskbestillinger)
                sisteSetteEventNokkelId = historikkvaskbestillinger.maxOf { it.eventlagerNøkkel ?: 0L }
                log.info("Vasket iterasjon med ${historikkvaskbestillinger.size} oppgaver. Har vasket totalt $vasketeller oppgaver")
            }
        }
        log.info("Historikkvask ferdig på ${tidsbruk}. Vasket totalt $vasketeller oppgaver")
    }

    private fun vaskBestillinger(vaskebestillinger: List<HistorikkvaskBestilling>): Int {
        // Kjøres sekvensielt – parallelitet styres av Jobbplanlegger som eier tråden vi kjører på.
        return vaskebestillinger.sumOf { historikkvaskBestilling ->
            try {
                vaskBestilling(historikkvaskBestilling)
            } catch (e: Exception) {
                log.error(
                    "HistorikkvaskVaktmester: Feil ved historikkvask for ${historikkvaskBestilling.eksternId} for fagsystem: ${historikkvaskBestilling.fagsystem}",
                    e
                )
                0
            }
        }
    }

    fun vaskBestilling(historikkvaskBestilling: HistorikkvaskBestilling): Int {
        val eventNøkkel = EventNøkkel(
            historikkvaskBestilling.fagsystem,
            historikkvaskBestilling.eksternId,
            historikkvaskBestilling.eventlagerNøkkel
        )
        val oppgavenøkkel = OppgaveNøkkelDto(
            historikkvaskBestilling.eksternId,
            K9Oppgavetypenavn.fraFagsystem(historikkvaskBestilling.fagsystem).kode,
            "K9"
        )

        var eventNrForBehandling = 0
        transactionalManager.transaction { tx ->
            oppgaveV3Tjeneste.slettOppgave(oppgavenøkkel, tx)
            eventRepository.settDirty(eventNøkkel, tx)
            eventNrForBehandling = eventTilOppgaveAdapter.oppdaterOppgaveForEksternIdUnderHistorikkvask(
                eventNøkkel, tx
            ).toInt()

            if (historikkvaskBestilling.eventlagerNøkkel != null) {
                eventRepository.settHistorikkvaskFerdig(historikkvaskBestilling.eventlagerNøkkel, tx)
            } else {
                eventRepository.settHistorikkvaskFerdig(
                    historikkvaskBestilling.fagsystem,
                    historikkvaskBestilling.eksternId,
                    tx
                )

            }
        }
        return eventNrForBehandling
    }
}