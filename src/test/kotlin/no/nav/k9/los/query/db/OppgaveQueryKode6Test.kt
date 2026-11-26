package no.nav.k9.los.oppgaveuthenting.query.db

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

@Disabled("Rød sone: fullføres sammen med den obligatoriske kode 6-sperren")
class OppgaveQueryKode6Test {
    @Test
    fun `kode 6-leiren får ikke ordinære oppgaver eller oppgaver uten cache`() {
        assertEquals(listOf(true), beskyttelseForTreff(skjermet = true))
    }

    @Test
    fun `ordinær leir får ikke kode 6 eller oppgaver uten cache`() {
        assertEquals(listOf(false), beskyttelseForTreff(skjermet = false))
    }

    @Test
    fun `nøstet OR kan ikke utvide tellingen til den andre leiren`() {
        assertEquals(1L, antallMedNøstetEller())
    }

    private fun beskyttelseForTreff(skjermet: Boolean): List<Boolean?> =
        TODO("Lag en oppgave per leir og én uten cache. Kjør repositoryet med tomt filter og serververifisert leir.")

    private fun antallMedNøstetEller(): Long =
        TODO("Kjør en telling med nøstet OR som inkluderer begge leirer i brukerens filtre.")
}
