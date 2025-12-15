package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import kotlinx.coroutines.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.HistorikkvaskBestilling
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.NyOppgaveversjon
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.VaskOppgaveversjon
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.measureTime

class HistorikkvaskTjeneste(
    private val eventRepository: EventRepository,
    private val oppgaveV3Tjeneste: OppgaveV3Tjeneste,
    private val eventTilOppgaveMapper: EventTilOppgaveMapper,
    private val transactionalManager: TransactionalManager,
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
        var forrigeOppgave: OppgaveV3? = null
        var oppgaveV3: OppgaveV3? = null

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
                for (event in eventer) {
                    val oppgaveversjon = eventTilOppgaveMapper.mapOppgave(event, forrigeOppgave, eventNrForBehandling)

                    when (oppgaveversjon) {
                        is NyOppgaveversjon -> {
                            val dto = oppgaveversjon.dto
                            forrigeOppgave =
                                oppgaveV3Tjeneste.vaskEksisterendeOppgaveversjon(dto, eventNrForBehandling, tx)
                            eventNrForBehandling++
                        }

                        is VaskOppgaveversjon -> {
                            val dto = oppgaveversjon.dto
                            forrigeOppgave =
                                oppgaveV3Tjeneste.vaskEksisterendeOppgaveversjon(dto, eventNrForBehandling - 1, tx)
                        }
                    }
                }

                oppgaveV3?.let {
                    oppgaveV3Tjeneste.ajourholdOppgave(oppgaveV3, eventNrForBehandling, tx)
                }
                eventRepository.settHistorikkvaskFerdig(
                    historikkvaskBestilling.fagsystem,
                    historikkvaskBestilling.eksternId
                )

            }
        }
        return eventNrForBehandling
    }
}