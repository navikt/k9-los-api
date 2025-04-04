package no.nav.k9.los.nyoppgavestyring.query.db

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.mapping.CombineOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.OppgavefilterListeUtvider
import org.junit.jupiter.api.Test

class OppgavefilterListeUtviderTest {

    @Test
    fun `Flere verdier med 'IN' skal endres til CombineOperator-OR med enverdi-filtre med EQUALS`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.IN, listOf("OPPR", "AVSLU"))
        )
        assertThat(OppgavefilterListeUtvider.utvid(oppgavefiltre).first()).isEqualTo(
            CombineOppgavefilter(
                CombineOperator.OR, filtere = listOf(
                FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf("OPPR")),
                FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf("AVSLU"))
            ))
        )
    }

    @Test
    fun `Kun oppgaver som inneholder alle verdier-'EQUALS' skal endres til CombineOperator-AND med enverdi-filtre med EQUALS`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, "aksjonspunkt", EksternFeltverdiOperator.EQUALS, listOf("5053", "5016"))
        )
        val resultat = OppgavefilterListeUtvider.utvid(oppgavefiltre)
        assertThat(resultat).containsExactly(
            CombineOppgavefilter(
                CombineOperator.AND, filtere = listOf(
                FeltverdiOppgavefilter(null, "aksjonspunkt", EksternFeltverdiOperator.EQUALS, listOf("5053")),
                FeltverdiOppgavefilter(null, "aksjonspunkt", EksternFeltverdiOperator.EQUALS, listOf("5016"))
            ))
        )
    }

    @Test
    fun `Oppgaver som ikke inneholder verdier-'NOT IN' skal endres til CombineOperator-AND med enverdi-filtre med NOT EQUALS`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.NOT_IN, listOf("OPPR", "AVSLU"))
        )
        assertThat(OppgavefilterListeUtvider.utvid(oppgavefiltre).first()).isEqualTo(
            CombineOppgavefilter(
                CombineOperator.AND, filtere = listOf(
                FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.NOT_EQUALS, listOf("OPPR")),
                FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.NOT_EQUALS, listOf("AVSLU"))
            ))
        )
    }

    @Test
    fun `Nested oppgavefiltre med 'IN' skal utvides`() {
        val oppgavefiltre = listOf(
            CombineOppgavefilter(
                CombineOperator.OR, filtere = listOf(
                FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.IN, listOf("OPPR", "AVSLU")),
                FeltverdiOppgavefilter(null, "aksjonspunkt", EksternFeltverdiOperator.EQUALS, listOf("5053", "5016"))
            ))
        )

        assertThat(OppgavefilterListeUtvider.utvid(oppgavefiltre).first()).isEqualTo(
            CombineOppgavefilter(
                CombineOperator.OR, filtere = listOf(
                CombineOppgavefilter(
                    CombineOperator.OR, filtere = listOf(
                    FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf("OPPR")),
                    FeltverdiOppgavefilter(null, "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf("AVSLU"))
                )),
                CombineOppgavefilter(
                    CombineOperator.AND, filtere = listOf(
                    FeltverdiOppgavefilter(null, "aksjonspunkt", EksternFeltverdiOperator.EQUALS, listOf("5053")),
                    FeltverdiOppgavefilter(null, "aksjonspunkt", EksternFeltverdiOperator.EQUALS, listOf("5016"))
                ))
            ))
        )
    }
}