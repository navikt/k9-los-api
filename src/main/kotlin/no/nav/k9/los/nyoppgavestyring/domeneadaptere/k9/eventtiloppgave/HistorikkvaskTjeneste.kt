package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import kotlinx.coroutines.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.HistorikkvaskBestilling
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk.StatistikkRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.NyOppgaveversjon
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.VaskOppgaveversjon
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.measureTime

class HistorikkvaskTjeneste(
    private val eventRepository: EventRepository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val statistikkRepository: StatistikkRepository,
    private val transactionalManager: TransactionalManager,
    private val eventTilOppgaveAdapter: EventTilOppgaveAdapter,
) {
    private val log: Logger = LoggerFactory.getLogger(HistorikkvaskTjeneste::class.java)

    fun kjørHistorikkvask() {
        val dispatcher = newFixedThreadPoolContext(5, "Historikkvask")

        val antallBestillinger = eventRepository.hentAntallHistorikkvaskbestillinger()
        log.info("Fant totalt $antallBestillinger historikkvaskbestillinger")

        var vasketeller = 0L
        val tidsbruk = measureTime {
            while (true) {
                val historikkvaskbestillinger =
                    eventRepository.hentAlleHistorikkvaskbestillinger(antall = 2000)
                if (historikkvaskbestillinger.isEmpty()) break

                log.info("Starter vaskeiterasjon på ${historikkvaskbestillinger.size} oppgaver")
                vaskBestillinger(historikkvaskbestillinger, dispatcher)
                vasketeller += historikkvaskbestillinger.size
                log.info("Vasket iterasjon med ${historikkvaskbestillinger.size} oppgaver. Har vasket totalt $vasketeller oppgaver")
            }
        }

        log.info("Historikkvask ferdig på ${tidsbruk}. Vasket totalt $vasketeller oppgaver")
    }

    private fun vaskBestillinger(
        vaskebestillinger: List<HistorikkvaskBestilling>,
        dispatcher: ExecutorCoroutineDispatcher,
    ): Int {
        val scope = CoroutineScope(dispatcher)

        val jobber = vaskebestillinger.map { historikkvaskBestilling ->
            scope.async {
                runBlocking {
                    try {
                        vaskBestilling(historikkvaskBestilling)
                    } catch (e: Exception) {
                        log.error("HistorikkvaskVaktmester: Feil ved historikkvask for ${historikkvaskBestilling.eksternId} for fagsystem: ${historikkvaskBestilling.fagsystem}", e)
                        0
                    }
                }
            }
        }.toList()

        val eventTeller = runBlocking {
            jobber.sumOf { it.await() }
        }

        return eventTeller
    }

    fun vaskBestilling(historikkvaskBestilling: HistorikkvaskBestilling): Int {
        var eventNrForBehandling = 0
        transactionalManager.transaction { tx ->
            val eventer =
                eventRepository.hentAlleEventerMedLås(
                    historikkvaskBestilling.fagsystem,
                    historikkvaskBestilling.eksternId,
                    tx
                )
            if (eventer.any { it.dirty }) {
                log.info("Avbryter historikkvask for ${historikkvaskBestilling.eksternId} for fagsystem: ${historikkvaskBestilling.fagsystem}. Historikkvasken har funnet eventer som ennå ikke er lastet inn med normalflyt. Dirty eventer skal håndteres av vanlig adaptertjeneste.")
            } else {
                val oppgavenøkkel = OppgaveNøkkelDto(
                    historikkvaskBestilling.eksternId,
                    K9Oppgavetypenavn.fraFagsystem(historikkvaskBestilling.fagsystem).kode,
                    "K9"
                )

                statistikkRepository.fjernSendtMarkering(oppgavenøkkel, tx)
                oppgaveV3Tjeneste.slettOppgave(oppgavenøkkel, tx)
                val eventNøkkel = EventNøkkel(historikkvaskBestilling.fagsystem, historikkvaskBestilling.eksternId)
                eventRepository.settDirty(eventNøkkel, tx)
                eventNrForBehandling = eventTilOppgaveAdapter.oppdaterOppgaveForEksternId(eventNøkkel, tx).toInt()

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