package no.nav.k9.los.tjenester.saksbehandler.oppgave

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.utils.forskyvReservasjonsDato
import no.nav.k9.los.utils.leggTilDagerHoppOverHelg
import org.junit.jupiter.api.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DateUtilKtTest {
    @Test
    internal fun sjekkAtViIkkeFårHelg() {
        1L.rangeTo(10L).forEach {            
            val dato = LocalDateTime.now().plusDays(it).forskyvReservasjonsDato()
            assert(dato.dayOfWeek != DayOfWeek.SATURDAY)
            assert(dato.dayOfWeek != DayOfWeek.SUNDAY)
        }
    }

    @Test
    fun `leggTilDagHoppOverHelg skal finne kl 2359 om n dager`() {
        val dato = LocalDateTime.of(LocalDate.of(2024, 5, 20), LocalTime.of(12, 0)) //en mandag

        val forskjøvet = dato.leggTilDagerHoppOverHelg(2)
        assert(forskjøvet.dayOfWeek == DayOfWeek.WEDNESDAY)
        assert(forskjøvet.toLocalTime() == LocalTime.of(23, 59))
    }

    @Test
    fun `leggTilDagHoppOverHelg skal ikke telle med lørdag og søndag`() {
        var dato = LocalDateTime.of(LocalDate.of(2024, 5, 16), LocalTime.of(12, 0)) //en torsdag

        var forskjøvet = dato.leggTilDagerHoppOverHelg(2)
        assertThat(forskjøvet.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assertThat(forskjøvet.toLocalTime()).isEqualTo(LocalTime.of(23, 59))
        assertThat(forskjøvet.toLocalDate()).isEqualTo(LocalDate.of(2024, 5, 20))

        dato = LocalDateTime.of(LocalDate.of(2024, 5, 17), LocalTime.of(12, 0)) //en fredag

        forskjøvet = dato.leggTilDagerHoppOverHelg(2)
        assertThat(forskjøvet.dayOfWeek).isEqualTo(DayOfWeek.TUESDAY)
        assertThat(forskjøvet.toLocalTime()).isEqualTo(LocalTime.of(23, 59))
        assertThat(forskjøvet.toLocalDate()).isEqualTo(LocalDate.of(2024, 5, 21))
    }

    @Test
    fun `leggTilDagHoppOverHelg med 0 dager skal gi kl 2359 samme dag`() {
        val dato = LocalDateTime.of(LocalDate.of(2024, 5, 20), LocalTime.of(12, 0)) //en mandag

        val forskjøvet = dato.leggTilDagerHoppOverHelg(0)
        assertThat(forskjøvet.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assert(forskjøvet.toLocalTime() == LocalTime.of(23, 59))
    }

    @Test
    fun `leggTilDagHoppOverHelg med 0 dager for en helgedag skal gi kl 2359 påfølgende mandag`() {
        var dato = LocalDateTime.of(LocalDate.of(2024, 5, 25), LocalTime.of(12, 0)) //en lørdag

        var forskjøvet = dato.leggTilDagerHoppOverHelg(0)
        assertThat(forskjøvet.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assert(forskjøvet.toLocalTime() == LocalTime.of(23, 59))
        assertThat(forskjøvet.toLocalDate()).isEqualTo(LocalDate.of(2024, 5, 27))

        dato = LocalDateTime.of(LocalDate.of(2024, 5, 26), LocalTime.of(12, 0)) //en søndag

        forskjøvet = dato.leggTilDagerHoppOverHelg(0)
        assertThat(forskjøvet.dayOfWeek).isEqualTo(DayOfWeek.MONDAY)
        assert(forskjøvet.toLocalTime() == LocalTime.of(23, 59))
        assertThat(forskjøvet.toLocalDate()).isEqualTo(LocalDate.of(2024, 5, 27))
    }
}