package no.nav.k9.los.nyoppgavestyring.query.mapping

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.mockk
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode
import no.nav.k9.los.nyoppgavestyring.query.db.OppgavefeltMedMer
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime

class OppgaveQueryToSqlMapperTest {

    @Test
    fun `ytelse - oppgaveQueryMapper må sette parameter oppgavestatus for bruk av indeks på oppgavefelt_verdi`() {
        val oppgaveQuery = OppgaveQuery(listOf(
            FeltverdiOppgavefilter("K9", "oppgavestatus", "EQUALS", listOf(Oppgavestatus.AAPEN.kode)),
            FeltverdiOppgavefilter("K9", "fagsystem", "NOT_EQUALS", listOf("Tullball")),
            CombineOppgavefilter("OR", listOf(
                FeltverdiOppgavefilter("K9", "mottattDato", "LESS_THAN", listOf(LocalDate.of(2022, 1, 1))),
                CombineOppgavefilter("AND", listOf(
                    FeltverdiOppgavefilter("K9", "oppgavestatus", "EQUALS", listOf(Oppgavestatus.LUKKET.kode)),
                ))
            ))
        ))

        val felter = mapOf<OmrådeOgKode, OppgavefeltMedMer>(
            OmrådeOgKode("K9", FeltType.OPPGAVE_STATUS.eksternId) to mockk(relaxed = true),
            OmrådeOgKode("K9", FeltType.FAGSYSTEM.eksternId) to mockk(relaxed = true),
            OmrådeOgKode("K9", FeltType.MOTTATT_DATO.eksternId) to mockk(relaxed = true),
        )

        val sqlOppgaveQuery = OppgaveQueryToSqlMapper.toSqlOppgaveQuery(
            QueryRequest(oppgaveQuery),
            felter,
            LocalDateTime.now()
        )

        assertThat(sqlOppgaveQuery.oppgavestatusFilter).isEqualTo(listOf(Oppgavestatus.AAPEN, Oppgavestatus.LUKKET))
    }

    @Test
    fun `ytelse - oppgaveQueryMapper må sette parameter oppgavestatus for bruk av indeks på oppgavefelt_verdi123`() {
        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilterK9(FeltType.OPPGAVE_STATUS, FeltverdiOperator.IN, Oppgavestatus.AAPEN.kode, Oppgavestatus.VENTER.kode))
        )

        val sqlOppgaveQuery = OppgaveQueryToSqlMapper.toSqlOppgaveQuery(
            QueryRequest(oppgaveQuery),
            mapOf(OmrådeOgKode("K9", FeltType.OPPGAVE_STATUS.eksternId) to mockk(relaxed = true)),
            LocalDateTime.now()
        )

        assertThat(sqlOppgaveQuery.oppgavestatusFilter).isEqualTo(listOf(Oppgavestatus.AAPEN, Oppgavestatus.VENTER))
    }

    private fun byggFilterK9(feltType: FeltType, feltverdiOperator: FeltverdiOperator, vararg verdier: String?): FeltverdiOppgavefilter {
        return FeltverdiOppgavefilter(
            "K9",
            feltType.eksternId,
            feltverdiOperator.name,
            verdier.toList()
        )
    }
}