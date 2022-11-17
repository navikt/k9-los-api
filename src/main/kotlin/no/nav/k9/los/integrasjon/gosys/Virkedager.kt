package no.nav.k9.los.integrasjon.gosys

import java.time.DayOfWeek
import java.time.LocalDate

internal object Virkedager {
    fun nVirkedagerFra(n: Int, dato: LocalDate): LocalDate {
        return if (n == 0) dato else nVirkedagerFra(n - 1, nesteUkedag(dato))
    }

    private fun nesteUkedag(dato: LocalDate): LocalDate {
        val nesteDag = dato.plusDays(1)
        return if (erHelg(nesteDag)) nesteUkedag(nesteDag) else nesteDag
    }

    private fun erHelg(dato: LocalDate): Boolean {
        return dato.dayOfWeek == DayOfWeek.SATURDAY || dato.dayOfWeek == DayOfWeek.SUNDAY
    }
}