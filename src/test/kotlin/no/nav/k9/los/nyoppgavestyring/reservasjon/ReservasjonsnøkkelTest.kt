package no.nav.k9.los.nyoppgavestyring.reservasjon

import assertk.assertThat
import assertk.assertions.doesNotContain
import org.junit.jupiter.api.Test

class ReservasjonsnøkkelTest {

    @Test
    fun `Skal ikke levere aktørId ved bruk i logg eller toString på klassen`() {
        val sensitivString = "12345"
        val nøkkel = Reservasjonsnøkkel("PSB_b_${sensitivString}_beslutter")
        assertThat(nøkkel.toString()).doesNotContain(sensitivString)
    }
}