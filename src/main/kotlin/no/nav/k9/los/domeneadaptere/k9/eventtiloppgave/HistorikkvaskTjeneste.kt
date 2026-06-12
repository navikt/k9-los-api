package no.nav.k9.los.domeneadaptere.k9.eventtiloppgave

import kotlinx.coroutines.*
import no.nav.k9.los.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.HistorikkvaskBestilling
import no.nav.k9.los.infrastruktur.db.DB_AWARE_PARALLELISM
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavemottak.OppgaveMottakTjeneste
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.measureTime

class HistorikkvaskTjeneste(
    private val eventRepository: EventRepository,
    private val oppgaveMottakTjeneste: OppgaveMottakTjeneste,
    private val transactionalManager: TransactionalManager,
    private val eventTilOppgaveAdapter: EventTilOppgaveAdapter,
) {
    private val log: Logger = LoggerFactory.getLogger(HistorikkvaskTjeneste::class.java)

    companion object {
        // Historikkvask kjører inne i Jobbplanleggeren som allerede bruker én coroutine-slot.
        // Vi reserverer ytterligere 1 slot for andre korte jobber som kan kjøre samtidig.
        private val PARALLELLE_VASKERE = (DB_AWARE_PARALLELISM - 1).coerceAtLeast(1)
        private const val BATCH_STORRELSE = 2000
    }

    fun kjørHistorikkvask() {
        val antallBestillinger = eventRepository.hentAntallHistorikkvaskbestillinger()
        if (antallBestillinger == 0L) {
            return
        }

        log.info("Fant totalt $antallBestillinger historikkvaskbestillinger")
        val dispatcher = Dispatchers.IO.limitedParallelism(PARALLELLE_VASKERE)
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
                vasketeller += vaskBestillinger(historikkvaskbestillinger, dispatcher)
                sisteSetteEventNokkelId = historikkvaskbestillinger.maxOf { it.eventlagerNøkkel ?: 0L }
                log.info("Vasket iterasjon med ${historikkvaskbestillinger.size} oppgaver. Har vasket totalt $vasketeller oppgaver")
            }
        }
        log.info("Historikkvask ferdig på ${tidsbruk}. Vasket totalt $vasketeller oppgaver")
    }

    private fun vaskBestillinger(
        vaskebestillinger: List<HistorikkvaskBestilling>,
        dispatcher: CoroutineDispatcher,
    ): Int {
        return runBlocking(dispatcher) {
            val jobber = vaskebestillinger.map { historikkvaskBestilling ->
                async {
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
            jobber.awaitAll().sum()
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
            oppgaveMottakTjeneste.slettOppgave(oppgavenøkkel, tx)
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