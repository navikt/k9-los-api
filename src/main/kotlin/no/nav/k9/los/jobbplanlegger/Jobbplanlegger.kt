package no.nav.k9.los.jobbplanlegger

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class Jobbplanlegger(
    private val scope: CoroutineScope,
    private val tidtaker: () -> LocalDateTime = { LocalDateTime.now() }
) {
    private val jobber = ConcurrentHashMap<String, JobbStatus>()
    private val aktivePrioriteter = ConcurrentHashMap<String, Int>()
    private val mutex = Mutex()
    private var hovedJob: Job? = null
    private val log = LoggerFactory.getLogger(Jobbplanlegger::class.java)

    fun planleggOppstartJobb(
        navn: String,
        prioritet: Int,
        blokk: suspend () -> Unit
    ) {
        val jobb = PlanlagtJobb.Oppstart(navn, prioritet, blokk)
        leggTilJobb(jobb, tidtaker())
    }

    fun planleggKjørFørTidspunktJobb(
        navn: String,
        prioritet: Int,
        tidsfrist: LocalDateTime,
        blokk: suspend () -> Unit
    ) {
        val jobb = PlanlagtJobb.KjørFørTidspunkt(navn, prioritet, tidsfrist, blokk)
        val nå = tidtaker()

        if (tidsfrist.isBefore(nå)) {
            log.info("Jobb '$navn' blir ikke lagt til. Jobben er satt til å kjøre ikke senere enn $tidsfrist.")
            return
        }

        leggTilJobb(jobb, nå)
    }

    fun planleggPeriodiskJobb(
        navn: String,
        prioritet: Int,
        intervall: Duration,
        startForsinkelse: Duration = intervall,
        tidsvindu: Tidsvindu? = null,
        blokk: suspend () -> Unit
    ) {
        val jobb = PlanlagtJobb.Periodisk(navn, prioritet, intervall, startForsinkelse, blokk)
        val nesteKjøring = tidtaker().plus(startForsinkelse.inWholeMilliseconds, ChronoUnit.MILLIS)
        leggTilJobb(jobb, nesteKjøring, tidsvindu)
    }

    fun planleggTimeJobb(
        navn: String,
        prioritet: Int,
        minutter: List<Int>,
        tidsvindu: Tidsvindu? = null,
        blokk: suspend () -> Unit
    ) {
        require(minutter.all { it in 0..59 }) { "Minutter må være mellom 0 og 59" }
        val jobb = PlanlagtJobb.TimeJobb(navn, prioritet, minutter.sorted(), blokk)
        val nesteKjøring = beregnNesteTimeKjøring(minutter)
        leggTilJobb(jobb, nesteKjøring, tidsvindu)
    }

    fun planleggKjørPåTidspunktJobb(
        navn: String,
        prioritet: Int,
        tidspunkt: LocalDateTime,
        blokk: suspend () -> Unit
    ) {
        val nå = tidtaker()
        if (tidspunkt.isBefore(nå)) {
            log.info("Jobb '$navn' blir ikke lagt til. Jobb er satt til å kjøre når $tidspunkt passerer.")
            return
        }
        val jobb = PlanlagtJobb.KjørPåTidspunkt(navn, prioritet, tidspunkt, blokk)
        leggTilJobb(jobb, tidspunkt)
    }

    private fun leggTilJobb(
        jobb: PlanlagtJobb,
        nesteKjøring: LocalDateTime,
        tidsvindu: Tidsvindu? = null
    ) {
        require(!jobber.containsKey(jobb.navn)) { "Flere jobber registreres med navn '${jobb.navn}'. De må være unike." }
        jobber[jobb.navn] = JobbStatus(jobb, tidsvindu, nesteKjøring)
    }

    fun hentKjøreplan(): Map<String, LocalDateTime?> = jobber.mapValues { it.value.nesteKjøring }

    fun start() {
        hovedJob = scope.launch {
            while (isActive) {
                val nå = tidtaker()
                val kjørbare = finnKjørbareJobber(nå)

                kjørbare.forEach { status ->
                    startJobb(status)
                }

                delay(1.seconds) // Sjekk hvert sekund
            }
        }
    }

    fun stopp() {
        hovedJob?.cancel()
        hovedJob = null
        jobber.values.forEach { it.erAktiv = false }
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
                    status.jobb.blokk()
                } finally {
                    mutex.withLock {
                        status.erAktiv = false
                        aktivePrioriteter.remove(status.jobb.navn)
                        oppdaterNesteKjøring(status)
                    }
                }
            }
        }
    }

    private fun finnKjørbareJobber(nå: LocalDateTime): List<JobbStatus> {
        return jobber.values.filter { status ->
            val erInnenTidsvindu = status.tidsvindu?.erInnenfor(nå) ?: true
            val kanKjøreNå = when (val jobb = status.jobb) {
                is PlanlagtJobb.KjørPåTidspunkt -> nå.isEqual(jobb.tidspunkt) || nå.isAfter(jobb.tidspunkt)
                else -> true
            }
            val erKlarTilKjøring = !status.erAktiv && (status.nesteKjøring?.isEqual(nå) == true || status.nesteKjøring?.isBefore(nå) == true)

            erKlarTilKjøring && erInnenTidsvindu && kanKjøreNå
        }.sortedBy { it.jobb.prioritet }
    }

    private fun oppdaterNesteKjøring(status: JobbStatus) {
        when (val jobb = status.jobb) {
            is PlanlagtJobb.Oppstart -> status.nesteKjøring = null
            is PlanlagtJobb.KjørFørTidspunkt -> status.nesteKjøring = null
            is PlanlagtJobb.KjørPåTidspunkt -> jobber.remove(jobb.navn)
            is PlanlagtJobb.Periodisk -> status.nesteKjøring = tidtaker()
                .plus(jobb.intervall.inWholeMilliseconds, ChronoUnit.MILLIS)

            is PlanlagtJobb.TimeJobb -> status.nesteKjøring = beregnNesteTimeKjøring(jobb.minutter)
        }
    }

    private fun beregnNesteTimeKjøring(minutter: List<Int>): LocalDateTime {
        val nå = tidtaker()
        val nesteMinutt = minutter.find { it > nå.minute } ?: minutter.first()
        return if (nesteMinutt > nå.minute) {
            nå.withMinute(nesteMinutt).withSecond(0).withNano(0)
        } else {
            nå.plusHours(1).withMinute(nesteMinutt).withSecond(0).withNano(0)
        }
    }
}