package no.nav.k9.los.nyoppgavestyring.query

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.*
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.query.mapping.EksternFeltverdiOperator
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

        val oppgaveQueryUtenStatusfilter = OppgaveQuery(
            listOf(
                byggFilter(
                    FeltType.TID_SIDEN_MOTTATT_DATO,
                    EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                    Duration.ofDays(11).toString()
                )
            )
        )
        val oppgaveQueryMedStatusfilter = OppgaveQuery(
            listOf(
                byggFilter(
                    FeltType.TID_SIDEN_MOTTATT_DATO,
                    EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                    Duration.ofDays(11).toString()
                ),
                byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.EQUALS, Oppgavestatus.AAPEN.kode)
            )
        )

        val result = oppgaveQueryRepository.query(QueryRequest(oppgaveQueryUtenStatusfilter))
        val resultMedStatusfilter = oppgaveQueryRepository.query(QueryRequest(oppgaveQueryMedStatusfilter))
        assertThat(result.size).isEqualTo(1)
        assertThat(resultMedStatusfilter.size).isEqualTo(1)

        assertThat(Duration.parse(hentOppgave(result[0]).hentVerdi("tidSidenMottattDato")!!).toDays()).isEqualTo(20)
        assertThat(
            Duration.parse(hentOppgave(resultMedStatusfilter[0]).hentVerdi("tidSidenMottattDato")!!).toDays()
        ).isEqualTo(20)
    }

    @Test
    fun `transient utleder løpende varighet where clause og hent data hele listen stigende`() {
        testdataLøpendeVarighet()

        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilter(
                    FeltType.TID_SIDEN_MOTTATT_DATO,
                    EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                    Duration.ofDays(9).toString()
                )
            ),
            listOf(
                byggOrder(FeltType.TID_SIDEN_MOTTATT_DATO, true)
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
                byggFilter(
                    FeltType.TID_SIDEN_MOTTATT_DATO,
                    EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                    Duration.ofDays(9).toString()
                )
            ),
            listOf(
                byggOrder(FeltType.TID_SIDEN_MOTTATT_DATO, false)
            )
        )

        val result = oppgaveQueryRepository.query(QueryRequest(oppgaveQuery))
        assertThat(result.size).isEqualTo(2)

        val oppgave = hentOppgave(result[0])

        assertThat(Duration.parse(oppgave.hentVerdi("tidSidenMottattDato")!!).toDays()).isEqualTo(20)
    }


    @Test
    fun `transient utleder løpende varighet select returnerer ISO 8601 duration`() {
        testdataLøpendeVarighet()

        val oppgaveQuery = OppgaveQuery(
            filtere = listOf(
                byggFilter(FeltType.OPPGAVE_STATUS, EksternFeltverdiOperator.EQUALS, Oppgavestatus.AAPEN.kode)
            ),
            select = listOf(
                EnkelSelectFelt(område = FeltType.TID_SIDEN_MOTTATT_DATO.område, kode = FeltType.TID_SIDEN_MOTTATT_DATO.eksternId)
            ),
            order = listOf(
                byggOrder(FeltType.TID_SIDEN_MOTTATT_DATO, false)
            )
        )

        val resultat = transactionalManager.transaction { tx ->
            oppgaveQueryRepository.queryForOppgaveResultat(tx, QueryRequest(oppgaveQuery), LocalDateTime.now())
        }

        assertThat(resultat.size).isEqualTo(2)
        val dagerFørste = Duration.parse(resultat[0].felter.first { it.kode == "tidSidenMottattDato" }.verdi as String).toDays()
        val dagerAndre = Duration.parse(resultat[1].felter.first { it.kode == "tidSidenMottattDato" }.verdi as String).toDays()
        assertThat(dagerFørste).isEqualTo(20)
        assertThat(dagerAndre).isEqualTo(10)
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
        val oppgaveRepository = get<OppgaveRepository>()
        val partisjonertRepository = get<PartisjonertOppgaveRepository>()

        return transactionalManager.transaction { tx ->
            when (id) {
                is OppgaveV3Id -> oppgaveRepository.hentOppgaveForId(tx, id)
                is PartisjonertOppgaveId -> {
                    val (oppgaveEksternId, oppgavetypeEksternId) = partisjonertRepository
                        .hentOppgaveEksternIdOgOppgavetype(id, tx)
                    oppgaveRepository.hentOppgaveForEksternIdOgOppgavetype(
                        tx,
                        oppgaveEksternId,
                        oppgavetypeEksternId
                    )
                }
            }

        }
    }
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

private fun byggOrder(feltType: FeltType, økende: Boolean): EnkelOrderFelt {
    return EnkelOrderFelt(
        område = feltType.område,
        feltType.eksternId,
        økende = økende
    )
}
