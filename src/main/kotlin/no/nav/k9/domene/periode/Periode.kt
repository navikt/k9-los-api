package no.nav.k9.domene.periode

import java.time.LocalDate
import java.util.stream.Stream

fun <T> Map<LocalDate, T>.tidligsteOgSeneste(): Periode? {
    val datoer = this.keys
    if (datoer.isEmpty()) return null

    return Periode(
        datoer.minByOrNull { it }!!,
        datoer.maxByOrNull { it }!!
    )
}

data class Periode(
    val fom: LocalDate,
    val tom: LocalDate
) {
    fun datoerIPeriode(): Stream<LocalDate> {
        return fom.datesUntil(tom.plusDays(1))
    }
}
