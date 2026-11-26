package no.nav.k9.los.oppgaveuthenting.query.db

import no.nav.k9.los.oppgaveuthenting.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.mapping.CombineOperator
import no.nav.k9.los.oppgaveuthenting.query.mapping.EksternFeltverdiOperator.*
import no.nav.k9.los.oppgaveuthenting.query.mapping.Personbeskyttelsefilter
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PersonbeskyttelsefilterTest {
    @Test
    fun `migrerer nøstede filtre og bevarer resten av spørringen`() {
        val ugradert = FeltverdiOppgavefilter(null, "personbeskyttelse", IN, listOf("UGRADERT"))
        val kombinert = FeltverdiOppgavefilter(null, "personbeskyttelse", EQUALS, listOf("KODE7_ELLER_EGEN_ANSATT"))
        val status = FeltverdiOppgavefilter(null, "oppgavestatus", IN, listOf("AAPEN"))
        val gammel = OppgaveQuery(
            filtere = listOf(CombineOppgavefilter(CombineOperator.OR, listOf(ugradert, kombinert)), status),
            order = listOf(EnkelOrderFelt(null, "sistEndret", false)),
        )
        val standard = FeltverdiOppgavefilter(null, "personbeskyttelse", NOT_IN, listOf("KODE7", "EGEN_ANSATT"))
        val forventet = gammel.copy(filtere = listOf(
            CombineOppgavefilter(CombineOperator.OR, listOf(standard, standard.copy(operator = IN))), status,
        ))

        val migrert = Personbeskyttelsefilter.migrer(gammel)

        assertEquals(forventet, migrert)
        assertEquals(migrert, Personbeskyttelsefilter.migrer(migrert))
        assertEquals(standard, Personbeskyttelsefilter.standard(kode6 = false))
    }

    @Test
    fun `migrerer negativt kombinasjonsvalg med flere verdier`() {
        val gammeltFilter = FeltverdiOppgavefilter(null, "personbeskyttelse", NOT_IN, listOf("KODE6", "KODE7_ELLER_EGEN_ANSATT"))
        val migrert = Personbeskyttelsefilter.migrer(OppgaveQuery(listOf(gammeltFilter)))

        assertEquals(listOf(gammeltFilter.copy(verdi = listOf("KODE6", "KODE7", "EGEN_ANSATT"))), migrert.filtere)
    }
}
