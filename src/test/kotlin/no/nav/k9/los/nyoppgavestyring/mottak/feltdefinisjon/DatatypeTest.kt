package no.nav.k9.los.nyoppgavestyring.oppgavedefinisjon.feltdefinisjon

import assertk.assertThat
import assertk.assertions.isEqualTo
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class DatatypeTest {
    @Test
    fun `skal mappe Integer`() {
        assertThat(Datatype.fraKode("Integer")).isEqualTo(Datatype.INTEGER)
    }

    @Test
    fun `skal ikke defaultmappe, men kaste exception`() {
        assertThrows<Exception> { Datatype.fraKode("foo") }
    }
}