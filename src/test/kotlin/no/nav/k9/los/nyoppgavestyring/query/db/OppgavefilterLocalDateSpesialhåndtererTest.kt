package no.nav.k9.los.nyoppgavestyring.query.db

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.felter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.hentFørsteMedOperator
import no.nav.k9.los.nyoppgavestyring.query.isEqualToDate
import no.nav.k9.los.nyoppgavestyring.query.mapping.CombineOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.OppgavefilterRens
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OppgavefilterLocalDateSpesialhåndtererTest {

    @Test
    fun `Date-verdier skal oversettes til DateTime ved EQUALS`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, FeltType.MOTTATT_DATO.eksternId, EksternFeltverdiOperator.EQUALS, listOf("2023-06-15"))
        )

        val combineFilter = OppgavefilterRens.rens(felter, oppgavefiltre).first() as CombineOppgavefilter
        assertThat(combineFilter.combineOperator).isEqualTo(CombineOperator.AND)
        val filtre = combineFilter.filtere.map { it as FeltverdiOppgavefilter }

        assertThat(filtre.hentFørsteMedOperator(EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-06-15T00:00:00"))
        assertThat(filtre.hentFørsteMedOperator(EksternFeltverdiOperator.LESS_THAN_OR_EQUALS).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-06-15T23:59:59.999"))
    }

    @Test
    fun `Date-verdier skal oversettes til DateTime ved GREATER_THAN_OR_EQAULS`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, FeltType.MOTTATT_DATO.eksternId, EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS, listOf("2023-06-15"))
        )

        val filter = OppgavefilterRens.rens(felter, oppgavefiltre).first() as FeltverdiOppgavefilter
        assertThat(filter.operator).isEqualTo(EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS)
        assertThat(filter.verdi.first()).isEqualToDate(LocalDateTime.parse("2023-06-15T00:00:00"))
    }

    @Test
    fun `Date-verdier skal oversettes til DateTime ved LESS_THAN_OR_EQAULS`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, FeltType.MOTTATT_DATO.eksternId, EksternFeltverdiOperator.LESS_THAN_OR_EQUALS, listOf("2023-06-15"))
        )

        val filter = OppgavefilterRens.rens(felter, oppgavefiltre).first() as FeltverdiOppgavefilter
        assertThat(filter.operator).isEqualTo(EksternFeltverdiOperator.LESS_THAN_OR_EQUALS)
        assertThat(filter.verdi.first()).isEqualToDate(LocalDateTime.parse("2023-06-15T23:59:59.999"))
    }


    @Test
    fun `Date-verdier skal oversettes til DateTime med foer og etter dato ved NOT_EQUALS`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, FeltType.MOTTATT_DATO.eksternId, EksternFeltverdiOperator.NOT_EQUALS, listOf("2023-06-15"))
        )

        val combineFilter = OppgavefilterRens.rens(felter, oppgavefiltre).first() as CombineOppgavefilter
        assertThat(combineFilter.combineOperator).isEqualTo(CombineOperator.OR)
        val filtre = combineFilter.filtere.map { it as FeltverdiOppgavefilter }

        assertThat(filtre.hentFørsteMedOperator(EksternFeltverdiOperator.LESS_THAN).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-06-15T00:00:00"))
        assertThat(filtre.hentFørsteMedOperator(EksternFeltverdiOperator.GREATER_THAN).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-06-15T23:59:59.999"))
    }

    @Test
    fun `Date-verdier oversettes ikke ved GREATER_THAN`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, FeltType.MOTTATT_DATO.eksternId, EksternFeltverdiOperator.GREATER_THAN, listOf("2023-06-15"))
        )

        val filter = OppgavefilterRens.rens(felter, oppgavefiltre).first() as FeltverdiOppgavefilter
        assertThat(filter.operator).isEqualTo(EksternFeltverdiOperator.GREATER_THAN)
        assertThat(filter.verdi.first()).isEqualToDate(LocalDateTime.parse("2023-06-15T23:59:59.999"))
    }

    @Test
    fun `Date-verdier oversettes ikke ved LESS_THAN`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, FeltType.MOTTATT_DATO.eksternId, EksternFeltverdiOperator.LESS_THAN, listOf("2023-06-15"))
        )

        val filter = OppgavefilterRens.rens(felter, oppgavefiltre).first() as FeltverdiOppgavefilter
        assertThat(filter.operator).isEqualTo(EksternFeltverdiOperator.LESS_THAN)
        assertThat(filter.verdi.first()).isEqualToDate(LocalDateTime.parse("2023-06-15T00:00:00"))
    }

    @Test
    fun `Verdier med andre typer endres ikke av date-utvidelse`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, FeltType.AKSJONSPUNKT.eksternId, EksternFeltverdiOperator.EQUALS, listOf("5016"))
        )

        val filter = OppgavefilterRens.rens(felter, oppgavefiltre).first() as FeltverdiOppgavefilter
        assertThat(filter.operator).isEqualTo(EksternFeltverdiOperator.EQUALS)
        assertThat(filter.verdi.first()).isEqualTo("5016")
    }

    @Test
    fun `Date-verdier i combiner skal utvides`() {
        val oppgavefiltre = listOf(
            CombineOppgavefilter(combineOperator = CombineOperator.AND, filtere = listOf(
                FeltverdiOppgavefilter(null, FeltType.MOTTATT_DATO.eksternId, EksternFeltverdiOperator.EQUALS, listOf("2023-05-05")),
                FeltverdiOppgavefilter(null, FeltType.REGISTRERT_DATO.eksternId, EksternFeltverdiOperator.LESS_THAN_OR_EQUALS, listOf("2023-05-06"))
            ))
        )

        val combineFilter = OppgavefilterRens.rens(felter, oppgavefiltre).first() as CombineOppgavefilter
        assertThat(combineFilter.combineOperator).isEqualTo(CombineOperator.AND)

        val equalsCombiner = combineFilter.filtere.first() as CombineOppgavefilter
        assertThat(equalsCombiner.combineOperator).isEqualTo(CombineOperator.AND)
        assertThat(equalsCombiner.hentFørsteMedOperator(EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-05-05T00:00:00"))
        assertThat(equalsCombiner.hentFørsteMedOperator(EksternFeltverdiOperator.LESS_THAN_OR_EQUALS).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-05-05T23:59:59.999"))

        val lessThanFilter = listOf(combineFilter.filtere.last() as FeltverdiOppgavefilter)
        assertThat(lessThanFilter.hentFørsteMedOperator(EksternFeltverdiOperator.LESS_THAN_OR_EQUALS).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-05-06T23:59:59.999"))
    }
}