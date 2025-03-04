package no.nav.k9.los.nyoppgavestyring.jobbplanlegger

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Jobbplanlegger(
    private val innkommendeJobber: Set<PlanlagtJobb>,
    coroutineContext: CoroutineContext,
    private val tidtaker: () -> LocalDateTime = { LocalDateTime.now() },
    private val ventetidMellomJobber: Duration = 1.seconds,
) {
    private val scope = CoroutineScope(coroutineContext + SupervisorJob())
    private val jobber = ConcurrentHashMap<String, JobbStatus>()
    private var planleggerJob: Job? = null
    private var erStartet = false
    private val log = LoggerFactory.getLogger(Jobbplanlegger::class.java)

    fun start() {
        if (erStartet) {
            log.warn("Jobbplanlegger er allerede startet")
            return
        }
        erStartet = true
        initialiserJobber()
        planleggerJob = scope.launch {
            while (isActive) {
                finnKjørbareJobber().forEach { startJobb(it) }
                delay(ventetidMellomJobber)
            }
        }
    }

    private fun initialiserJobber() {
        val nå = tidtaker()
        innkommendeJobber.forEach { jobb ->
            when (val kjøretidspunkt = jobb.førsteKjøretidspunkt(nå)) {
                is Kjøretidspunkt.KlarTilKjøring -> {
                    jobber[jobb.navn] = JobbStatus(jobb, kjøretidspunkt)
                    log.info("Initialiser jobb: ${jobb.navn} er klar til kjøring")
                }

                is Kjøretidspunkt.KjørIFremtiden -> {
                    jobber[jobb.navn] = JobbStatus(jobb, kjøretidspunkt)
                    log.info("Initialiser jobb: ${jobb.navn} har kjøretidspunkt ${kjøretidspunkt.tidspunkt}")
                }

                is Kjøretidspunkt.SkalIkkeKjøres -> {
                    log.info("Initialiser jobb: ${jobb.navn} skal ikke kjøres")
                }
            }
        }
    }

    fun stopp() {
        if (!erStartet) return
        erStartet = false
        planleggerJob?.cancel()
        planleggerJob = null
        jobber.values.forEach { it.erAktiv = false }
        jobber.clear()
    }

    private fun startJobb(status: JobbStatus) {
        if (status.erAktiv) return
        status.erAktiv = true

        val exceptionHandler = CoroutineExceptionHandler { _, exception ->
            log.error("Feil ved kjøring av jobb ${status.jobb.navn}", exception)
        }
        scope.launch(exceptionHandler) {
            try {
                JobbMetrikker.timeSuspended(status.jobb.navn) {
                    status.jobb.blokk(scope)
                }
            } finally {
                status.erAktiv = false
                when (val nesteKjøretidspunkt = status.jobb.nesteKjøretidspunkt(tidtaker())) {
                    is Kjøretidspunkt.KjørIFremtiden, Kjøretidspunkt.KlarTilKjøring -> {
                        status.nesteKjøring = nesteKjøretidspunkt
                    }

                    is Kjøretidspunkt.SkalIkkeKjøres -> {
                        jobber.remove(status.jobb.navn)
                    }
                }
            }
        }
    }

    private fun finnKjørbareJobber(): List<JobbStatus> {
        val nå = tidtaker()
        return jobber.values
            .filter { it.kanKjøres(nå) }
            .filter { kjørerIngenJobberMedHøyerePrioritet(it) }
            .groupBy { it.jobb.prioritet }
            .minByOrNull { it.key }
            ?.value
            .orEmpty()
    }

    private fun kjørerIngenJobberMedHøyerePrioritet(jobbStatus: JobbStatus) =
        jobber.values.none { it.erAktiv && it.jobb.prioritet < jobbStatus.jobb.prioritet }
}