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
                FeltverdiOppgavefilter("K9", "oppgavestatus", "EQUALS", listOf(Oppgavestatus.AAPEN.kode)),
                FeltverdiOppgavefilter("K9", "fagsystem", "NOT_EQUALS", listOf("Tullball")),
                CombineOppgavefilter(
                    "OR", listOf(
                        FeltverdiOppgavefilter("K9", "mottattDato", "LESS_THAN", listOf(LocalDate.of(2022, 1, 1))),
                        CombineOppgavefilter(
                            "AND", listOf(
                                FeltverdiOppgavefilter(
                                    "K9",
                                    "oppgavestatus",
                                    "EQUALS",
                                    listOf(Oppgavestatus.LUKKET.kode)
                                ),
                            )
                        )
                    )
                )
            )
        )

        val oppgavestatuser = OppgaveQueryToSqlMapper.traverserFiltereOgFinnOppgavestatusfilter(
            QueryRequest(oppgaveQuery),
        )

        assertThat(oppgavestatuser).containsOnly(Oppgavestatus.AAPEN, Oppgavestatus.LUKKET)
    }

    @Test
    fun `skal traversere hele treet med filtre og sette alle som betingelser`() {
        val oppgaveQuery = OppgaveQuery(
            listOf(
                // 2 betingelser
                byggFilter(
                    FeltType.OPPGAVE_STATUS,
                    FeltverdiOperator.IN,
                    Oppgavestatus.AAPEN.kode,
                    Oppgavestatus.VENTER.kode
                ),
                // 2 betingelser (starten og slutten på dagen)
                byggFilter(FeltType.MOTTATT_DATO, FeltverdiOperator.EQUALS, "2024-12-24"),
                // 4 betingelser
                byggFilter(FeltType.YTELSE_TYPE, FeltverdiOperator.IN, "PSB", "OMP", "FOO", "BAR"),
            )
        )
        val sqlBuilder = OppgaveQueryToSqlMapper.toSqlOppgaveQuery(
            QueryRequest(oppgaveQuery),
            felter,
            LocalDateTime.now()
        )

        assertThat(sqlBuilder.getQuery()).contains(sqlBuilder.getParams().keys)
        assertThat(sqlBuilder.getParams()).hasSize(8 * 3) // totalt 8 betingelser, hver av de har parameter for feltkode, område og verdi
    }

    private fun byggFilter(
        feltType: FeltType,
        feltverdiOperator: FeltverdiOperator,
        vararg verdier: String?
    ): FeltverdiOppgavefilter {
        return FeltverdiOppgavefilter(
            feltType.område,
            feltType.eksternId,
            feltverdiOperator.name,
            verdier.toList()
        )
    }
}