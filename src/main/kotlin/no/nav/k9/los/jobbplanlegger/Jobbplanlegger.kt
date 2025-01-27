package no.nav.k9.los.jobbplanlegger

import io.ktor.util.collections.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import no.nav.k9.los.jobbplanlegger.PlanlagtJobb.KjørPåTidspunkt
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Jobbplanlegger(
    private val scope: CoroutineScope,
    private val tidtaker: () -> LocalDateTime = { LocalDateTime.now() },
    private val ventetidMellomJobber: Duration = 1.seconds
) {
    private val jobber = ConcurrentSet<JobbStatus>()
    private val aktivePrioriteter = ConcurrentHashMap<String, Int>()
    private val mutex = Mutex()
    private var hovedJob: Job? = null
    private val log = LoggerFactory.getLogger(Jobbplanlegger::class.java)

    fun planleggOppstartJobb(
        navn: String,
        prioritet: Int,
        blokk: suspend CoroutineScope.() -> Unit
    ) {
        leggTilJobb(PlanlagtJobb.Oppstart(navn = navn, prioritet = prioritet, blokk = blokk))
    }

    fun planleggPeriodiskJobb(
        navn: String,
        prioritet: Int,
        intervall: Duration,
        startForsinkelse: Duration = intervall,
        tidsvindu: Tidsvindu = Tidsvindu.ÅPENT,
        blokk: suspend CoroutineScope.() -> Unit
    ) {
        if (intervall < ventetidMellomJobber) {
            log.warn("Intervall for jobb '$navn' ($intervall) er kortere enn ventetiden mellom jobber ($ventetidMellomJobber)")
        }
        val jobb = PlanlagtJobb.Periodisk(
            navn = navn,
            prioritet = prioritet,
            tidsvindu = tidsvindu,
            intervall = intervall,
            startForsinkelse = startForsinkelse,
            blokk = blokk
        )
        leggTilJobb(jobb)
    }

    fun planleggTimeJobb(
        navn: String,
        prioritet: Int,
        minutter: List<Int>,
        tidsvindu: Tidsvindu = Tidsvindu.ÅPENT,
        blokk: suspend CoroutineScope.() -> Unit
    ) {
        require(minutter.all { it in 0..59 }) { "Minutter må være mellom 0 og 59" }
        leggTilJobb(PlanlagtJobb.TimeJobb(
            navn = navn,
            prioritet = prioritet,
            tidsvindu = tidsvindu,
            minutter = minutter.sorted(),
            blokk = blokk
        ))
    }

    fun planleggKjørPåTidspunktJobb(
        navn: String,
        prioritet: Int,
        kjørTidligst: LocalDateTime = LocalDateTime.MIN,
        kjørSenest: LocalDateTime = LocalDateTime.MAX,
        blokk: suspend CoroutineScope.() -> Unit
    ) {
        require(kjørSenest > kjørTidligst) { "kjørSenest må være etter kjørTidligst" }
        val jobb = KjørPåTidspunkt(navn, prioritet, kjørTidligst, kjørSenest, blokk)
        leggTilJobb(jobb)
    }

    private fun leggTilJobb(
        jobb: PlanlagtJobb,
    ) {
        require(jobber.none { it.jobb.navn == jobb.navn }) { "Flere jobber registrert med navn '${jobb.navn}'. De må være unike." }
        jobber.add(JobbStatus(jobb, nesteKjøring = LocalDateTime.MAX))
    }

    fun hentKjøreplan(): Map<String, LocalDateTime?> = jobber.associate { it.jobb.navn to it.nesteKjøring }

    fun start() {
        val nå = tidtaker()
        jobber.forEach { jobbStatus ->
            val førsteKjøretidspunkt = jobbStatus.jobb.førsteKjøretidspunkt(nå)
            if (førsteKjøretidspunkt == null) {
                jobber.remove(jobbStatus)
            } else {
                jobbStatus.nesteKjøring = førsteKjøretidspunkt
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
        jobber.forEach { it.erAktiv = false }
        aktivePrioriteter.clear()
    }

    private fun kanStarteJobb(jobbPrioritet: Int): Boolean {
        return aktivePrioriteter.values.none { aktivPrioritet -> aktivPrioritet < jobbPrioritet }
    }

    private suspend fun startJobb(status: JobbStatus) = mutex.withLock {
        if (!status.erAktiv && kanStarteJobb(status.jobb.prioritet)) {
            status.erAktiv = true
            aktivePrioriteter[status.jobb.navn] = status.jobb.prioritet

            scope.launch {
                try {
                    status.jobb.blokk(this)
                } catch (e: Exception) {
                    log.error("Feil ved kjøring av jobb ${status.jobb.navn}", e)
                } finally {
                    mutex.withLock {
                        status.erAktiv = false
                        aktivePrioriteter.remove(status.jobb.navn)
                        val nesteKjøretidspunkt = status.jobb.nesteKjøretidspunkt(tidtaker())
                        if (nesteKjøretidspunkt == null) {
                            jobber.remove(status)
                        } else {
                            status.nesteKjøring = nesteKjøretidspunkt
                        }
                    }
                }
            }
        }
    }

    private fun finnKjørbareJobber(): List<JobbStatus> {
        val nå = tidtaker()
        return jobber.filter { status -> status.nesteKjøring <= nå}.sortedBy { it.jobb.prioritet }
    }
}