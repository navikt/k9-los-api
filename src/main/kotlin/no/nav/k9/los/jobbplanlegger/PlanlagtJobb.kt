package no.nav.k9.los.jobbplanlegger

import kotlinx.coroutines.CoroutineScope
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.toJavaDuration

sealed class Kjøretidspunkt {
    abstract fun kanKjøres(nå: LocalDateTime): Boolean

    data object SkalIkkeKjøres : Kjøretidspunkt() {
        override fun kanKjøres(nå: LocalDateTime) = false
    }

    data object KlarTilKjøring : Kjøretidspunkt() {
        override fun kanKjøres(nå: LocalDateTime) = true
    }

    data class KjørIFremtiden(val tidspunkt: LocalDateTime) : Kjøretidspunkt() {
        override fun kanKjøres(nå: LocalDateTime): Boolean = tidspunkt <= nå
    }
}

abstract class PlanlagtJobb(
    val navn: String,
    val prioritet: Int,
    val blokk: suspend CoroutineScope.() -> Unit,
) {
    /** Kjøres når systemet er klar for at jobben kjøres. Dersom tidspunktet som  */
    abstract fun førsteKjøretidspunkt(nå: LocalDateTime): Kjøretidspunkt
    /** Kjøres rett etter at jobben er ferdig */
    abstract fun nesteKjøretidspunkt(nå: LocalDateTime): Kjøretidspunkt

    operator fun LocalDateTime.plus(duration: Duration): LocalDateTime {
        return this.plus(duration.toJavaDuration())
    }

    class Oppstart(
        navn: String,
        prioritet: Int,
        blokk: suspend CoroutineScope.() -> Unit
    ) : PlanlagtJobb(navn, prioritet, blokk) {
        override fun førsteKjøretidspunkt(nå: LocalDateTime) = Kjøretidspunkt.KlarTilKjøring
        override fun nesteKjøretidspunkt(nå: LocalDateTime) = Kjøretidspunkt.SkalIkkeKjøres
    }

    class KjørPåTidspunkt(
        navn: String,
        prioritet: Int,
        private val kjørTidligst: LocalDateTime = LocalDateTime.MIN,
        private val kjørSenest: LocalDateTime = LocalDateTime.MAX,
        blokk: suspend CoroutineScope.() -> Unit
    ) : PlanlagtJobb(navn, prioritet, blokk) {
        init {
            require(kjørSenest > kjørTidligst) { "kjørSenest må være etter kjørTidligst" }
        }

        override fun førsteKjøretidspunkt(nå: LocalDateTime): Kjøretidspunkt {
            return when {
                nå > kjørSenest -> Kjøretidspunkt.SkalIkkeKjøres
                kjørTidligst > nå -> Kjøretidspunkt.KjørIFremtiden(kjørTidligst)
                else -> Kjøretidspunkt.KlarTilKjøring
            }
        }
        override fun nesteKjøretidspunkt(nå: LocalDateTime) = Kjøretidspunkt.SkalIkkeKjøres
    }

    class Periodisk(
        navn: String,
        prioritet: Int,
        private val tidsvindu: Tidsvindu = Tidsvindu.ÅPENT,
        val intervall: Duration,
        private val startForsinkelse: Duration,
        blokk: suspend CoroutineScope.() -> Unit
    ) : PlanlagtJobb(navn, prioritet, blokk) {
        override fun førsteKjøretidspunkt(nå: LocalDateTime): Kjøretidspunkt {
            if (tidsvindu.erInnenfor(nå) && startForsinkelse == Duration.ZERO) {
                return Kjøretidspunkt.KlarTilKjøring
            }
            return Kjøretidspunkt.KjørIFremtiden(maxOf(nå + startForsinkelse, tidsvindu.nesteÅpningITidsvindu(nå)))
        }

        override fun nesteKjøretidspunkt(nå: LocalDateTime): Kjøretidspunkt {
            return Kjøretidspunkt.KjørIFremtiden(maxOf(nå + intervall, tidsvindu.nesteÅpningITidsvindu(nå)))
        }
    }

    class TimeJobb(
        navn: String,
        prioritet: Int,
        private val tidsvindu: Tidsvindu,
        private val minutter: List<Int>,
        blokk: suspend CoroutineScope.() -> Unit
    ) : PlanlagtJobb(navn, prioritet, blokk) {
        init {
            require(minutter.all { it in 0..59 }) { "Minutter må være mellom 0 og 59" }
        }

        override fun førsteKjøretidspunkt(nå: LocalDateTime) = Kjøretidspunkt.KjørIFremtiden(beregnNesteTimeKjøring(nå, minutter))
        override fun nesteKjøretidspunkt(nå: LocalDateTime) = Kjøretidspunkt.KjørIFremtiden(beregnNesteTimeKjøring(nå, minutter))

        private fun beregnNesteTimeKjøring(nå: LocalDateTime, minutter: List<Int>): LocalDateTime {
            val nesteMinutt = minutter.find { it > nå.minute } ?: minutter.first()
            val nesteTidspunkt = if (nesteMinutt > nå.minute) {
                nå.withMinute(nesteMinutt).withSecond(0).withNano(0)
            } else {
                nå.plusHours(1).withMinute(nesteMinutt).withSecond(0).withNano(0)
            }
            val nesteÅpneTidspunkt = tidsvindu.nesteÅpningITidsvindu(nå)

            return maxOf(nesteTidspunkt, nesteÅpneTidspunkt)
        }
    }
}
