package no.nav.k9.los.nyoppgavestyring.query.db

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import no.nav.k9.los.nyoppgavestyring.query.mapping.OppgavefilterListeUtvider
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.mapping.CombineOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator
import org.junit.jupiter.api.Test

class OppgavefilterListeUtviderTest {

    @Test
    fun `Flere verdier med 'IN' skal endres til CombineOperator-OR med enverdi-filtre med EQUALS`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, "oppgavestatus", FeltverdiOperator.IN.name, listOf("OPPR", "AVSLU"))
        )
        assertThat(OppgavefilterListeUtvider.eliminer(oppgavefiltre).first()).isEqualTo(
            CombineOppgavefilter(
                CombineOperator.OR.kode, filtere = listOf(
                FeltverdiOppgavefilter(null, "oppgavestatus", FeltverdiOperator.EQUALS.name, listOf("OPPR")),
                FeltverdiOppgavefilter(null, "oppgavestatus", FeltverdiOperator.EQUALS.name, listOf("AVSLU"))
            ))
        )
    }

    @Test
    fun `Kun oppgaver som inneholder alle verdier-'EQUALS' skal endres til CombineOperator-AND med enverdi-filtre med EQUALS`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, "aksjonspunkt", FeltverdiOperator.EQUALS.name, listOf("5053", "5016"))
        )
        val resultat = OppgavefilterListeUtvider.eliminer(oppgavefiltre)
        assertThat(resultat).containsExactly(
            CombineOppgavefilter(
                CombineOperator.AND.kode, filtere = listOf(
                FeltverdiOppgavefilter(null, "aksjonspunkt", FeltverdiOperator.EQUALS.name, listOf("5053")),
                FeltverdiOppgavefilter(null, "aksjonspunkt", FeltverdiOperator.EQUALS.name, listOf("5016"))
            ))
        )
    }

    @Test
    fun `Oppgaver som ikke inneholder verdier-'NOT IN' skal endres til CombineOperator-AND med enverdi-filtre med NOT EQUALS`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, "oppgavestatus", FeltverdiOperator.NOT_IN.name, listOf("OPPR", "AVSLU"))
        )
        assertThat(OppgavefilterListeUtvider.eliminer(oppgavefiltre).first()).isEqualTo(
            CombineOppgavefilter(
                CombineOperator.AND.kode, filtere = listOf(
                FeltverdiOppgavefilter(null, "oppgavestatus", FeltverdiOperator.NOT_EQUALS.name, listOf("OPPR")),
                FeltverdiOppgavefilter(null, "oppgavestatus", FeltverdiOperator.NOT_EQUALS.name, listOf("AVSLU"))
            ))
        )
    }

    @Test
    fun `Nested oppgavefiltre med 'IN' skal utvides`() {
        val oppgavefiltre = listOf(
            CombineOppgavefilter(
                CombineOperator.OR.kode, filtere = listOf(
                FeltverdiOppgavefilter(null, "oppgavestatus", FeltverdiOperator.IN.name, listOf("OPPR", "AVSLU")),
                FeltverdiOppgavefilter(null, "aksjonspunkt", FeltverdiOperator.EQUALS.name, listOf("5053", "5016"))
            ))
        )

        assertThat(OppgavefilterListeUtvider.eliminer(oppgavefiltre).first()).isEqualTo(
            CombineOppgavefilter(
                CombineOperator.OR.kode, filtere = listOf(
                CombineOppgavefilter(
                    CombineOperator.OR.kode, filtere = listOf(
                    FeltverdiOppgavefilter(null, "oppgavestatus", FeltverdiOperator.EQUALS.name, listOf("OPPR")),
                    FeltverdiOppgavefilter(null, "oppgavestatus", FeltverdiOperator.EQUALS.name, listOf("AVSLU"))
                )),
                CombineOppgavefilter(
                    CombineOperator.AND.kode, filtere = listOf(
                    FeltverdiOppgavefilter(null, "aksjonspunkt", FeltverdiOperator.EQUALS.name, listOf("5053")),
                    FeltverdiOppgavefilter(null, "aksjonspunkt", FeltverdiOperator.EQUALS.name, listOf("5016"))
                ))
            ))
        )
    }
}