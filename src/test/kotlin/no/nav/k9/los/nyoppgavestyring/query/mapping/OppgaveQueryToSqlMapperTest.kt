package no.nav.k9.los.nyoppgavestyring.query.mapping

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsOnly
import assertk.assertions.hasSize
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.felter
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode
import no.nav.k9.los.nyoppgavestyring.query.db.OppgavefeltMedMer
import no.nav.k9.los.nyoppgavestyring.query.dto.felter.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class OppgaveQueryToSqlMapperTest {

    @Test
    fun `må finne riktige oppgavestatuser, for å utlede oppgave- og oppgavefeltverditabeller`() {
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
    fun `ytelse - oppgaveQueryMapper må sette parameter oppgavestatus for bruk av indeks på oppgavefelt_verdi123`() {
        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilterK9(
                    FeltType.OPPGAVE_STATUS,
                    FeltverdiOperator.IN,
                    Oppgavestatus.AAPEN.kode,
                    Oppgavestatus.VENTER.kode
                )
            )
        )

        val oppgavefelt = Oppgavefelt(
            område = "K9",
            kode = FeltType.OPPGAVE_STATUS.eksternId,
            visningsnavn = FeltType.OPPGAVE_STATUS.name,
            tolkes_som = FeltType.OPPGAVE_STATUS.tolkesSom,
            kokriterie = true,
            verdiforklaringerErUttømmende = false,
            verdiforklaringer = emptyList()
        )
        val sqlOppgaveQuery = OppgaveQueryToSqlMapper.toSqlOppgaveQuery(
            QueryRequest(oppgaveQuery),
            mapOf(OmrådeOgKode("K9", FeltType.OPPGAVE_STATUS.eksternId) to OppgavefeltMedMer(oppgavefelt, null)),
            LocalDateTime.now()
        )

        assertThat(sqlOppgaveQuery.oppgavestatusFilter).containsOnly(Oppgavestatus.AAPEN, Oppgavestatus.VENTER)
    }

    @Test
    fun `skal traversere hele treet med filtre og sette alle som betingelser`() {
        val oppgaveQuery = OppgaveQuery(
            listOf(
                // 2 betingelser
                byggFilterK9(
                    FeltType.OPPGAVE_STATUS,
                    FeltverdiOperator.IN,
                    Oppgavestatus.AAPEN.kode,
                    Oppgavestatus.VENTER.kode
                ),
                // 2 betingelser (starten og slutten på dagen)
                byggFilterK9(FeltType.MOTTATT_DATO, FeltverdiOperator.EQUALS, "2024-12-24"),
                // 4 betingelser
                byggFilterK9(FeltType.YTELSE_TYPE, FeltverdiOperator.IN, "PSB", "OMP", "FOO", "BAR"),
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

    private fun byggFilterK9(
        feltType: FeltType,
        feltverdiOperator: FeltverdiOperator,
        vararg verdier: String?
    ): FeltverdiOppgavefilter {
        return FeltverdiOppgavefilter(
            "K9",
            feltType.eksternId,
            feltverdiOperator.name,
            verdier.toList()
        )
    }
}