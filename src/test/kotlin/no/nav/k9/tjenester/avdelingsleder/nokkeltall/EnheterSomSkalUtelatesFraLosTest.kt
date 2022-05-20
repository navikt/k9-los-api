package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import org.junit.jupiter.api.Test

internal class EnheterSomSkalUtelatesFraLosTest {

    @Test
    fun filtrerBortViken() {
        assertThat(EnheterSomSkalUtelatesFraLos.sjekkKanBrukes("2103 VIKEN")).isFalse()
    }

    @Test
    fun filtrerBortVikenKunId() {
        assertThat(EnheterSomSkalUtelatesFraLos.sjekkKanBrukes("2103")).isFalse()
    }

    @Test
    fun filtrerIkkeBortKristiania() {
        assertThat(EnheterSomSkalUtelatesFraLos.sjekkKanBrukes("2104 Kristiania")).isTrue()
    }
}