package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import kotlinx.coroutines.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.HistorikkvaskBestilling
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
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
                val historikkvaskbestillinger = eventRepository.hentAlleHistorikkvaskbestillinger(antall = 2000) //TODO: Hent bare tilbakekrav for pilottest
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
    ) : Int {
        val scope = CoroutineScope(dispatcher)

        val jobber = vaskebestillinger.map { historikkvaskBestilling ->
            scope.async { runBlocking { vaskBestilling(historikkvaskBestilling) } }
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
            val høyesteInternversjon = oppgaveV3Tjeneste.hentHøyesteInternVersjon(
                historikkvaskBestilling.eksternId,
                K9Oppgavetypenavn.fraFagsystem(historikkvaskBestilling.fagsystem).kode,
                "K9",
                tx)!!
            val eventer =
                eventRepository.hentAlleEventerMedLås(historikkvaskBestilling.fagsystem, historikkvaskBestilling.eksternId, tx)

            for (event in eventer) {
                if (eventNrForBehandling > høyesteInternversjon) {
                    log.info("Avbryter historikkvask for ${event.eksternId} ved eventTid ${event.eksternVersjon}. Forventer at håndteres av vanlig adaptertjeneste.")
                    break //Historikkvasken har funnet eventer som ennå ikke er lastet inn med normalflyt. Dirty eventer skal håndteres av vanlig adaptertjeneste
                }

                val oppgaveDto = eventTilOppgaveMapper.mapOppgave(event, forrigeOppgave)

                oppgaveV3 = oppgaveV3Tjeneste.utledEksisterendeOppgaveversjon(oppgaveDto, eventNrForBehandling, tx)
                oppgaveV3Tjeneste.oppdaterEksisterendeOppgaveversjon(oppgaveV3, eventNrForBehandling, tx)

                forrigeOppgave = oppgaveV3Tjeneste.hentOppgaveversjon(
                    område = "K9",
                    oppgavetype = oppgaveDto.type,
                    eksternId = oppgaveDto.eksternId,
                    eksternVersjon = oppgaveDto.eksternVersjon,
                    tx = tx
                )
                eventNrForBehandling++
            }
            oppgaveV3?.let {
                oppgaveV3Tjeneste.ajourholdOppgave(oppgaveV3, eventNrForBehandling, tx)
            }

            eventRepository.settHistorikkvaskFerdig(historikkvaskBestilling.fagsystem, historikkvaskBestilling.eksternId)
        }
        return eventNrForBehandling
    }
}