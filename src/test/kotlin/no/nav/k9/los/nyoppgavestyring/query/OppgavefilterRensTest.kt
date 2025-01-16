package no.nav.k9.los.nyoppgavestyring.query

import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.support.fail
import no.nav.k9.los.domene.lager.oppgave.v2.equalsWithPrecision
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.felter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.mapping.CombineOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.query.mapping.OppgavefilterRens
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.fail

class OppgavefilterRensTest {

    @Test
    fun `Oppgavefiltre med flere verdier av dato skal utvides med combiner for dato og verdier`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, FeltType.MOTTATT_DATO.eksternId, FeltverdiOperator.IN.name, listOf("2023-05-05", "2023-05-07"))
        )

        val combineFilter = OppgavefilterRens.rens(felter, oppgavefiltre).first() as CombineOppgavefilter
        assertThat(combineFilter.combineOperator).isEqualTo(CombineOperator.OR.kode)

        val (førsteDatoKombiner, sisteDatoKombiner) = combineFilter.filtere.map { it as CombineOppgavefilter }.apply { first() to last() }
        assertThat(førsteDatoKombiner.combineOperator).isEqualTo(CombineOperator.AND.kode)
        assertThat(førsteDatoKombiner.hentFørsteMedOperator(FeltverdiOperator.GREATER_THAN_OR_EQUALS).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-05-05T00:00:00"))
        assertThat(førsteDatoKombiner.hentFørsteMedOperator(FeltverdiOperator.LESS_THAN_OR_EQUALS).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-05-05T23:59:59.999"))

        assertThat(sisteDatoKombiner.combineOperator).isEqualTo(CombineOperator.AND.kode)
        assertThat(sisteDatoKombiner.hentFørsteMedOperator(FeltverdiOperator.GREATER_THAN_OR_EQUALS).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-05-07T00:00:00"))
        assertThat(sisteDatoKombiner.hentFørsteMedOperator(FeltverdiOperator.LESS_THAN_OR_EQUALS).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-05-07T23:59:59.999"))
    }

    @Test
    fun `Oppgavefiltre skal kun gi oppgaver som ikke matche noen av datoerverdier`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, FeltType.MOTTATT_DATO.eksternId, FeltverdiOperator.NOT_EQUALS.name, listOf("2023-05-05", "2023-05-07"))
        )

        val combineFilter = OppgavefilterRens.rens(felter, oppgavefiltre).first() as CombineOppgavefilter
        assertThat(combineFilter.combineOperator).isEqualTo(CombineOperator.AND.kode)

        val (førsteDatoKombiner, sisteDatoKombiner) = combineFilter.filtere.map { it as CombineOppgavefilter }.apply { first() to last() }
        assertThat(førsteDatoKombiner.combineOperator).isEqualTo(CombineOperator.OR.kode)
        assertThat(førsteDatoKombiner.hentFørsteMedOperator(FeltverdiOperator.LESS_THAN).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-05-05T00:00:00"))
        assertThat(førsteDatoKombiner.hentFørsteMedOperator(FeltverdiOperator.GREATER_THAN).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-05-05T23:59:59.999"))

        assertThat(sisteDatoKombiner.combineOperator).isEqualTo(CombineOperator.OR.kode)
        assertThat(sisteDatoKombiner.hentFørsteMedOperator(FeltverdiOperator.LESS_THAN).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-05-07T00:00:00"))
        assertThat(sisteDatoKombiner.hentFørsteMedOperator(FeltverdiOperator.GREATER_THAN).verdi.first()).isEqualToDate(LocalDateTime.parse("2023-05-07T23:59:59.999"))
    }

    @Test
    fun `Oppgavefiltre skal håndtere intervall-operator`() {
        val oppgavefiltre = listOf(
            FeltverdiOppgavefilter(null, FeltType.MOTTATT_DATO.eksternId, EksternFeltverdiOperator.INTERVAL.name, listOf("2023-05-05", "2023-05-07"))
        )

        val combineFilter = OppgavefilterRens.rens(felter, oppgavefiltre).first() as CombineOppgavefilter
        assertThat(combineFilter.combineOperator).isEqualTo(CombineOperator.AND.kode)

        val (førsteDatoKombiner, sisteDatoKombiner) = combineFilter.filtere.map { it as FeltverdiOppgavefilter }.apply { first() to last() }
        assertThat(førsteDatoKombiner.operator).isEqualTo(FeltverdiOperator.GREATER_THAN_OR_EQUALS.name)
        assertThat(førsteDatoKombiner.verdi).containsOnlyDate(LocalDateTime.parse("2023-05-05T00:00"))

        assertThat(sisteDatoKombiner.operator).isEqualTo(FeltverdiOperator.LESS_THAN_OR_EQUALS.name)
        assertThat(sisteDatoKombiner.verdi).containsOnlyDate(LocalDateTime.parse("2023-05-07T23:59:59.999"))
    }
}


internal fun CombineOppgavefilter.hentFørsteMedOperator(operator: FeltverdiOperator) = filtere.map { it as FeltverdiOppgavefilter }.hentFørsteMedOperator(operator)

internal fun List<FeltverdiOppgavefilter>.hentFørsteMedOperator(operator: FeltverdiOperator) = first { it.operator == operator.name }

internal fun Assert<List<Any?>>.containsOnlyDate(expected: LocalDateTime) = given { actual ->
    actual.forEach {
        if (it !is LocalDateTime) {
            fail("Er ikke LocalDateTime")
        }
        if (!it.equalsWithPrecision(expected, 10)) {
            fail(expected, actual)
        }
    }
}

internal fun Assert<Any?>.isEqualToDate(expected: LocalDateTime) = given { actual ->
    if (actual !is LocalDateTime) {
        fail("Er ikke LocalDateTime")
    }
    if (!actual.equalsWithPrecision(expected, 10)) {
        fail(expected, actual)
    }
}