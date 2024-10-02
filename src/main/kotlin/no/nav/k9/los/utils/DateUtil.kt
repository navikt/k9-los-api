package no.nav.k9.los.utils

import java.time.DayOfWeek
import java.time.LocalDateTime

@Deprecated("bruk LeggTilDagerHoppOverHelg i stedet")
fun LocalDateTime.forskyvReservasjonsDato(): LocalDateTime {
    var localDate = this.toLocalDate()
    while (localDate.dayOfWeek == DayOfWeek.SATURDAY || localDate.dayOfWeek == DayOfWeek.SUNDAY) {       
        localDate = localDate.plusDays(1)
    }

    return localDate.atStartOfDay().plusHours(23).plusMinutes(59)
}

fun LocalDateTime.leggTilDagerHoppOverHelg(dager: Int): LocalDateTime {
    if (dager == 0) {
        if (this.dayOfWeek == DayOfWeek.SATURDAY) {
            return this.plusDays(2).toLocalDate().atTime(23, 59)
        }
        if (this.dayOfWeek == DayOfWeek.SUNDAY) {
            return this.plusDays(1).toLocalDate().atTime(23, 59)
        }
    }

    var i = 0
    var nyDato = this.toLocalDate()
    while (i < dager) {
        nyDato = nyDato.plusDays(1)
        if (nyDato.dayOfWeek != DayOfWeek.SATURDAY && nyDato.dayOfWeek != DayOfWeek.SUNDAY) {
            i++
        }
    }
    return nyDato.atTime(23, 59)
}

