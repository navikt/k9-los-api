package no.nav.k9.los.nyoppgavestyring.query.mapping

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.mockk
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OppgaveQuieryToSqlMapperTest {

    @Test
    fun `ytelse - oppgavequery uten filter på oppgavestatus må ha param for oppgavequery for bruk av index i oppgavefelt_verdi123`() {
        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilterK9(FeltType.OPPGAVE_STATUS, FeltverdiOperator.IN, Oppgavestatus.AAPEN.kode, Oppgavestatus.VENTER.kode))
        )

        val sqlOppgaveQuery = OppgaveQueryToSqlMapper.toSqlOppgaveQuery(oppgaveQuery, mapOf(OmrådeOgKode("K9", FeltType.OPPGAVE_STATUS.eksternId) to mockk(relaxed = true)), LocalDateTime.now())

        assertThat(sqlOppgaveQuery.getParams()["oppgavestatus"]).isEqualTo(listOf(Oppgavestatus.entries.joinToString { it.kode }))
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