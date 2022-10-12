package no.nav.k9.los.tjenester.saksbehandler.oppgave

import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDateTime

class DateUtilKtTest {
    @Test
    internal fun sjekkAtViIkkeFårHelg() {
        1L.rangeTo(10L).forEach {            
            val dato = LocalDateTime.now().plusDays(it).forskyvReservasjonsDato()
            assert(dato.dayOfWeek != DayOfWeek.SATURDAY)
            assert(dato.dayOfWeek != DayOfWeek.SUNDAY)
        }
    }
}