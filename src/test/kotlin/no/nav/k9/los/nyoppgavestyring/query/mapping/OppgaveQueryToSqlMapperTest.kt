package no.nav.k9.los.nyoppgavestyring.query.mapping

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.mockk
import no.nav.k9.los.nyoppgavestyring.FeltType
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
            OmrådeOgKode("K9", FeltType.OPPGAVE_STATUS.eksternId) to OppgavefeltMedMer(Oppgavefelt(
                område = "K9",
                kode = FeltType.OPPGAVE_STATUS.eksternId,
                visningsnavn = FeltType.OPPGAVE_STATUS.name,
                tolkes_som = FeltType.OPPGAVE_STATUS.tolkesSom,
                kokriterie = true,
                verdiforklaringerErUttømmende = false,
                verdiforklaringer = emptyList()
            ), null),
            OmrådeOgKode("K9", FeltType.FAGSYSTEM.eksternId) to OppgavefeltMedMer(Oppgavefelt(
                område = "K9",
                kode = FeltType.FAGSYSTEM.eksternId,
                visningsnavn = FeltType.FAGSYSTEM.name,
                tolkes_som = FeltType.FAGSYSTEM.tolkesSom,
                kokriterie = true,
                verdiforklaringerErUttømmende = false,
                verdiforklaringer = emptyList()
            ), null),
            OmrådeOgKode("K9", FeltType.MOTTATT_DATO.eksternId) to OppgavefeltMedMer(Oppgavefelt(
                område = "K9",
                kode = FeltType.MOTTATT_DATO.eksternId,
                visningsnavn = FeltType.MOTTATT_DATO.name,
                tolkes_som = FeltType.MOTTATT_DATO.tolkesSom,
                kokriterie = true,
                verdiforklaringerErUttømmende = false,
                verdiforklaringer = emptyList()
            ), null),
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