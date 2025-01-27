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
    private var hovedJob: Job? = null
    private var erStartet = false
    private val log = LoggerFactory.getLogger(Jobbplanlegger::class.java)

    fun start() {
        if (erStartet) {
            log.warn("Jobbplanlegger er allerede startet")
            return
        }
        erStartet = true

        val nå = tidtaker()
        planlagteJobber.forEach { jobb ->
            jobb.førsteKjøretidspunkt(nå)?.let { tid ->
                jobber[jobb.navn] = JobbStatus(jobb, tid)
            }
        }

        hovedJob = scope.launch {
            while (isActive) {
                finnKjørbareJobber().forEach { startJobb(it) }
                delay(ventetidMellomJobber)
            }
        }
    }

    fun stopp() {
        if (!erStartet) return
        erStartet = false
        hovedJob?.cancel()
        hovedJob = null
        jobber.values.forEach { it.erAktiv = false }
        jobber.clear()
    }

    private fun startJobb(status: JobbStatus) {
        if (status.erAktiv) return

        status.erAktiv = true
        scope.launch {
            try {
                status.jobb.blokk(this)
            } finally {
                status.erAktiv = false
                val nesteKjøretidspunkt = status.jobb.nesteKjøretidspunkt(tidtaker())
                if (nesteKjøretidspunkt == null) {
                    jobber.remove(status.jobb.navn)
                } else {
                    status.nesteKjøring = nesteKjøretidspunkt
                }
            }
        }
    }

    private fun finnKjørbareJobber(): List<JobbStatus> {
        val nå = tidtaker()
        return jobber.values
            .filter { status ->
                status.nesteKjøring <= nå && !status.erAktiv &&
                        jobber.values.none { it.erAktiv && it.jobb.prioritet < status.jobb.prioritet }
            }
            .groupBy { it.jobb.prioritet }
            .minByOrNull { it.key }
            ?.value
            .orEmpty()
    }
}