package no.nav.k9.los.nyoppgavestyring.query

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotliquery.queryOf
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveId
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.AktivOppgaveRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveId
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Id
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.FeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.Duration
import java.time.LocalDateTime

class TransientFeltutlederTest : AbstractK9LosIntegrationTest() {

    private lateinit var transactionalManager: TransactionalManager
    private lateinit var oppgaveQueryRepository: OppgaveQueryRepository

    @BeforeEach
    fun setup() {
        transactionalManager = get()
        oppgaveQueryRepository = get()
    }

    //Følgende tester tester LøpendeDurationTransientFeltutleder via en av subklassene
    @Test
    fun `transient utleder løpende varighet where clause og hent data halve listen`() {
        testdataLøpendeVarighet()

        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilterK9(FeltType.TID_SIDEN_MOTTATT_DATO, FeltverdiOperator.GREATER_THAN_OR_EQUALS, Duration.ofDays(11).toString())
            )
        )

        val result = oppgaveQueryRepository.query(QueryRequest(oppgaveQuery))
        assertThat(result.size).isEqualTo(1)

        val oppgave = hentOppgave(result[0])

        assertThat(Duration.parse(oppgave.hentVerdi("tidSidenMottattDato")!!).toDays()).isEqualTo(20)
    }

    @Test
    fun `transient utleder løpende varighet where clause og hent data hele listen stigende`() {
        testdataLøpendeVarighet()

        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilterK9(FeltType.TID_SIDEN_MOTTATT_DATO, FeltverdiOperator.GREATER_THAN_OR_EQUALS, Duration.ofDays(9).toString())
            ),
            listOf(
                byggOrderK9(FeltType.TID_SIDEN_MOTTATT_DATO, true)
            )
        )

        val result = oppgaveQueryRepository.query(QueryRequest(oppgaveQuery))
        assertThat(result.size).isEqualTo(2)

        val oppgave = hentOppgave(result[0])

        assertThat(Duration.parse(oppgave.hentVerdi("tidSidenMottattDato")!!).toDays()).isEqualTo(10)
    }

    @Test
    fun `transient utleder løpende varighet where clause og hent data hele listen synkende`() {
        testdataLøpendeVarighet()

        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilterK9(FeltType.TID_SIDEN_MOTTATT_DATO, FeltverdiOperator.GREATER_THAN_OR_EQUALS, Duration.ofDays(9).toString())
            ),
            listOf(
                byggOrderK9(FeltType.TID_SIDEN_MOTTATT_DATO, false)
            )
        )

        val result = oppgaveQueryRepository.query(QueryRequest(oppgaveQuery))
        assertThat(result.size).isEqualTo(2)

        val oppgave = hentOppgave(result[0])

        assertThat(Duration.parse(oppgave.hentVerdi("tidSidenMottattDato")!!).toDays()).isEqualTo(20)
    }


    private fun testdataLøpendeVarighet() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, LocalDateTime.now().minusDays(20).toString())
            .lagOgLagre()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.MOTTATT_DATO, LocalDateTime.now().minusDays(10).toString())
            .lagOgLagre()
    }

    private fun testdataBeslutter() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.AKSJONSPUNKT, "5016")
            .medOppgaveFeltVerdi(FeltType.LØSBART_AKSJONSPUNKT, "5016")
            .lagOgLagre()

        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.AKSJONSPUNKT, "5015")
            .medOppgaveFeltVerdi(FeltType.LØSBART_AKSJONSPUNKT, "5015")
            .lagOgLagre()
    }

    private fun hentOppgave(id: OppgaveId): Oppgave {
        val aktivOppgaveRepository = get<AktivOppgaveRepository>()
        val oppgaveRepository = get<OppgaveRepository>()

        val oppgave = transactionalManager.transaction { tx ->
            when (id) {
                is AktivOppgaveId -> aktivOppgaveRepository.hentOppgaveForId(tx, id)
                is OppgaveV3Id -> oppgaveRepository.hentOppgaveForId(tx, id)
            }
        }
        return oppgave
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

    private fun byggOrderK9(feltType: FeltType, økende: Boolean): EnkelOrderFelt {
        return EnkelOrderFelt(
            område = "K9",
            feltType.eksternId,
            økende = økende
        )
    }
}