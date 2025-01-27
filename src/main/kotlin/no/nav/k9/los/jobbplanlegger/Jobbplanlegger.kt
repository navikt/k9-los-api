package no.nav.k9.los.jobbplanlegger

import kotlinx.coroutines.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Jobbplanlegger(
    private val planlagteJobber: Set<PlanlagtJobb>,
    private val scope: CoroutineScope,
    private val tidtaker: () -> LocalDateTime = { LocalDateTime.now() },
    private val ventetidMellomJobber: Duration = 1.seconds,
) {
    private val jobber = ConcurrentHashMap<String, JobbStatus>()
    private val aktivePrioriteter = ConcurrentHashMap<String, Int>()
    private var hovedJob: Job? = null
    private val log = LoggerFactory.getLogger(Jobbplanlegger::class.java)

    fun start() {
        val nå = tidtaker()
        planlagteJobber.forEach { jobb ->
            val førsteKjøretidspunkt = jobb.førsteKjøretidspunkt(nå)
            if (førsteKjøretidspunkt != null) {
                jobber[jobb.navn] = JobbStatus(jobb, førsteKjøretidspunkt)
            }
        }
        hovedJob = scope.launch {
            while (isActive) {
                val kjørbare = finnKjørbareJobber()
                kjørbare.forEach { status ->
                    startJobb(status)
                }
                delay(ventetidMellomJobber)
            }
        }
    }

    fun stopp() {
        hovedJob?.cancel()
        hovedJob = null
        jobber.values.forEach { jobbstatus -> jobbstatus.erAktiv = false }
        jobber.clear() // Tømmer jobb-settet
        aktivePrioriteter.clear()
    }

    private fun kanStarteJobbMedPrioritet(jobbPrioritet: Int): Boolean {
        return aktivePrioriteter.values.none { aktivPrioritet -> aktivPrioritet < jobbPrioritet }
    }

    private fun startJobb(status: JobbStatus) {
        if (!status.erAktiv && kanStarteJobbMedPrioritet(status.jobb.prioritet)) {
            status.erAktiv = true
            aktivePrioriteter[status.jobb.navn] = status.jobb.prioritet

            scope.launch {
                try {
                    status.jobb.blokk(this)
                } catch (e: Exception) {
                    log.error("Feil ved kjøring av jobb ${status.jobb.navn}", e)
                } finally {
                    status.erAktiv = false
                    aktivePrioriteter.remove(status.jobb.navn)
                    val nesteKjøretidspunkt = status.jobb.nesteKjøretidspunkt(tidtaker())
                    if (nesteKjøretidspunkt == null) {
                        jobber.remove(status.jobb.navn)
                    } else {
                        status.nesteKjøring = nesteKjøretidspunkt
                    }
                }
            }
        }
    }

    private fun finnKjørbareJobber(): List<JobbStatus> {
        val nå = tidtaker()
        return jobber.values.filter { status -> status.nesteKjøring <= nå }.sortedBy { it.jobb.prioritet }
    }
}