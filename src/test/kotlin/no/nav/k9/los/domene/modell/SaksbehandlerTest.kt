package no.nav.k9.los.domene.modell

import assertk.assertThat
import assertk.assertions.doesNotContain
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import org.junit.jupiter.api.Test

class SaksbehandlerTest {

    @Test // Enhet skal aldri logges. Navn og epost bør heller ikke logges
    fun `Skal kun bruke brukerIdent ved toString eller logging`() {
        val sensitiv = "SENSITIV"
        val sb = Saksbehandler(id = 123L,
            brukerIdent = "Test",
            navn = sensitiv,
            epost = sensitiv,
            enhet = sensitiv,
            reservasjoner = mutableSetOf(),
        )

        assertThat(sb.toString()).doesNotContain(sensitiv)
    }
}