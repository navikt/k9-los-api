package no.nav.k9.los.tjenester.saksbehandler.oppgave

import java.time.DayOfWeek
import java.time.LocalDateTime

fun LocalDateTime.forskyvReservasjonsDato(): LocalDateTime {
    var localDate = this.toLocalDate()
    while (localDate.dayOfWeek == DayOfWeek.SATURDAY || localDate.dayOfWeek == DayOfWeek.SUNDAY) {       
        localDate = localDate.plusDays(1)
    }

    return localDate.atStartOfDay().plusHours(23).plusMinutes(59)
}
