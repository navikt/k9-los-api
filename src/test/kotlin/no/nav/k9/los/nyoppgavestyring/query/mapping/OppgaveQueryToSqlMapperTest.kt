package no.nav.k9.los.nyoppgavestyring.query.mapping

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.felter
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class OppgaveQueryToSqlMapperTest {

    @Test
    fun `finner riktige oppgavestatuser, for å utlede oppgave- og oppgavefeltverditabeller`() {
        val oppgaveQuery = OppgaveQuery(
            listOf(
                FeltverdiOppgavefilter("K9", "oppgavestatus", EksternFeltverdiOperator.EQUALS, listOf(Oppgavestatus.AAPEN.kode)),
                FeltverdiOppgavefilter("K9", "fagsystem", EksternFeltverdiOperator.NOT_EQUALS, listOf("Tullball")),
                CombineOppgavefilter(
                    CombineOperator.OR, listOf(
                        FeltverdiOppgavefilter("K9", "mottattDato", EksternFeltverdiOperator.LESS_THAN, listOf(LocalDate.of(2022, 1, 1))),
                        CombineOppgavefilter(
                            CombineOperator.AND, listOf(
                                FeltverdiOppgavefilter(
                                    "K9",
                                    "oppgavestatus",
                                    EksternFeltverdiOperator.EQUALS,
                                    listOf(Oppgavestatus.LUKKET.kode)
                                ),
                            )
                        )
                    )
                )
            )
        )

        val oppgavestatuser = OppgaveQueryToSqlMapper.traverserFiltereOgFinnOppgavestatus(
            QueryRequest(oppgaveQuery),
        )

        assertThat(oppgavestatuser).containsOnly(Oppgavestatus.AAPEN, Oppgavestatus.LUKKET)
    }

    @Test
    fun `skal traversere hele treet med filtre og sette alle som betingelser, for aktiv-tabell`() {
        val oppgaveQuery = OppgaveQuery(
            listOf(
                // 2 betingelser
                byggFilter(
                    FeltType.OPPGAVE_STATUS,
                    EksternFeltverdiOperator.IN,
                    Oppgavestatus.AAPEN.kode,
                    Oppgavestatus.VENTER.kode
                ),
                // 2 betingelser (starten og slutten på dagen) med feltkode, område og verdi
                byggFilter(FeltType.MOTTATT_DATO, EksternFeltverdiOperator.EQUALS, "2024-12-24"),
                // 4 betingelser med feltkode, område og verdi
                byggFilter(FeltType.YTELSE_TYPE, EksternFeltverdiOperator.IN, "PSB", "OMP", "FOO", "BAR"),
            )
        )
        val sqlBuilder = OppgaveQueryToSqlMapper.toSqlOppgaveQuery(
            QueryRequest(oppgaveQuery),
            felter,
            LocalDateTime.now()
        )

        assertThat(sqlBuilder.getQuery()).contains(sqlBuilder.getParams().keys)
        assertThat(sqlBuilder.getParams()).hasSize(20)
    }

    @Test
    fun `skal traversere hele treet med filtre og sette alle som betingelser, for partisjonert-tabell`() {
        val oppgaveQuery = OppgaveQuery(
            listOf(
                // 3 betingelser på status på oppgave_v3
                byggFilter(
                    FeltType.OPPGAVE_STATUS,
                    EksternFeltverdiOperator.IN,
                    Oppgavestatus.AAPEN.kode,
                    Oppgavestatus.VENTER.kode,
                    Oppgavestatus.LUKKET.kode,
                ),
                // 2 betingelser (starten og slutten på dagen) med feltkode og verdi
                byggFilter(FeltType.MOTTATT_DATO, EksternFeltverdiOperator.EQUALS, "2024-12-24"),
                // feltkode, 4 verdier
                byggFilter(FeltType.YTELSE_TYPE, EksternFeltverdiOperator.IN, "PSB", "OMP", "FOO", "BAR"),
            )
        )
        val sqlBuilder = OppgaveQueryToSqlMapper.toSqlOppgaveQuery(
            QueryRequest(oppgaveQuery),
            felter,
            LocalDateTime.now()
        )

        assertThat(sqlBuilder.getQuery()).contains(sqlBuilder.getParams().keys)
        assertThat(sqlBuilder.getParams()).hasSize(12)
    }

    private fun byggFilter(
        feltType: FeltType,
        operator: EksternFeltverdiOperator,
        vararg verdier: String?
    ): FeltverdiOppgavefilter {
        return FeltverdiOppgavefilter(
            feltType.område,
            feltType.eksternId,
            operator,
            verdier.toList()
        )
    }
}