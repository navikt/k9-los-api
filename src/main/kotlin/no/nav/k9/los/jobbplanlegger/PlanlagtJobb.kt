package no.nav.k9.los.jobbplanlegger

import java.time.LocalDateTime
import kotlin.time.Duration

sealed class PlanlagtJobb(
    val navn: String,
    val prioritet: Int,
    val blokk: suspend () -> Unit
) {
    class Oppstart(
        navn: String,
        prioritet: Int,
        blokk: suspend () -> Unit
    ) : PlanlagtJobb(navn, prioritet, blokk)

    class KjørFørTidspunkt(
        navn: String,
        prioritet: Int,
        val tidspunkt: LocalDateTime,
        blokk: suspend () -> Unit
    ) : PlanlagtJobb(navn, prioritet, blokk)

    class KjørPåTidspunkt(
        navn: String,
        prioritet: Int,
        val tidspunkt: LocalDateTime,
        blokk: suspend () -> Unit
    ) : PlanlagtJobb(navn, prioritet, blokk)

    class Periodisk(
        navn: String,
        prioritet: Int,
        val intervall: Duration,
        val startForsinkelse: Duration,
        blokk: suspend () -> Unit
    ) : PlanlagtJobb(navn, prioritet, blokk)

    class TimeJobb(
        navn: String,
        prioritet: Int,
        val minutter: List<Int>,
        blokk: suspend () -> Unit
    ) : PlanlagtJobb(navn, prioritet, blokk)
}
