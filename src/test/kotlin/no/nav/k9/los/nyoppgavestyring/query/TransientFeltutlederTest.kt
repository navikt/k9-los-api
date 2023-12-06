package no.nav.k9.los.nyoppgavestyring.query

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.mockk
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.query.db.FeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelOrderFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.io.StringWriter
import java.time.Duration
import java.time.LocalDateTime
import java.time.temporal.TemporalUnit

class TransientFeltutlederTest : AbstractK9LosIntegrationTest() {

    private lateinit var transactionalManager: TransactionalManager

    @BeforeEach
    fun setup() {
        transactionalManager = get()
    }

    @Test
    fun `transient utleder ligger hos beslutter hente data`() {
        testdataBeslutter()

        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilterK9(FeltType.aksjonspunkt, FeltverdiOperator.EQUALS, "5016")
            )
        )

        val result = kjørQuery(oppgaveQuery)
        assertThat(result).isNotEmpty()

        val oppgave = hentOppgave(result[0])

        assertThat(oppgave.hentVerdi("liggerHosBeslutter").toBoolean()).isTrue()
    }

    @Test
    fun `teste transient utleder ligger hos beslutter Where clause`() {
        testdataBeslutter()

        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilterK9(FeltType.liggerHosBeslutter, FeltverdiOperator.EQUALS, "true")
            )
        )

        val result = kjørQuery(oppgaveQuery)
        assertThat(result.size).isEqualTo(1)

        val oppgave = hentOppgave(result[0])

        assertThat(oppgave.hentVerdi("liggerHosBeslutter").toBoolean()).isTrue()
    }

    @Test
    fun `teste transient utleder ligger hos beslutter order by clause økende`() {
        testdataBeslutter()

        val oppgaveQuery = OppgaveQuery(
            listOf(),
            listOf(
                byggOrderK9(FeltType.liggerHosBeslutter, true)
            )
        )

        val result = kjørQuery(oppgaveQuery)
        assertThat(result.size).isEqualTo(2)

        val oppgave = hentOppgave(result[0])

        assertThat(oppgave.hentVerdi("liggerHosBeslutter").toBoolean()).isFalse()
    }

    @Test
    fun `teste transient utleder ligger hos beslutter order by clause synkende`() {
        testdataBeslutter()

        val oppgaveQuery = OppgaveQuery(
            listOf(),
            listOf(
                byggOrderK9(FeltType.liggerHosBeslutter, false)
            )
        )

        val result = kjørQuery(oppgaveQuery)
        assertThat(result.size).isEqualTo(2)

        val oppgave = hentOppgave(result[0])

        assertThat(oppgave.hentVerdi("liggerHosBeslutter").toBoolean()).isTrue()
    }

    //Følgende tester tester LøpendeDurationTransientFeltutleder via en av subklassene
    @Test
    fun `transient utleder løpende varighet where clause og hent data halve listen`() {
        testdataLøpendeVarighet()

        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilterK9(FeltType.tidSidenMottattDato, FeltverdiOperator.GREATER_THAN_OR_EQUALS, Duration.ofDays(11).toString())
            )
        )

        val result = kjørQuery(oppgaveQuery)
        assertThat(result.size).isEqualTo(1)

        val oppgave = hentOppgave(result[0])

        assertThat(Duration.parse(oppgave.hentVerdi("tidSidenMottattDato")).toDays()).isEqualTo(20)
    }

    @Test
    fun `transient utleder løpende varighet where clause og hent data hele listen stigende`() {
        testdataLøpendeVarighet()

        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilterK9(FeltType.tidSidenMottattDato, FeltverdiOperator.GREATER_THAN_OR_EQUALS, Duration.ofDays(9).toString())
            ),
            listOf(
                byggOrderK9(FeltType.tidSidenMottattDato, true)
            )
        )

        val result = kjørQuery(oppgaveQuery)
        assertThat(result.size).isEqualTo(2)

        val oppgave = hentOppgave(result[0])

        assertThat(Duration.parse(oppgave.hentVerdi("tidSidenMottattDato")).toDays()).isEqualTo(10)
    }

    @Test
    fun `transient utleder løpende varighet where clause og hent data hele listen synkende`() {
        testdataLøpendeVarighet()

        val oppgaveQuery = OppgaveQuery(
            listOf(
                byggFilterK9(FeltType.tidSidenMottattDato, FeltverdiOperator.GREATER_THAN_OR_EQUALS, Duration.ofDays(9).toString())
            ),
            listOf(
                byggOrderK9(FeltType.tidSidenMottattDato, false)
            )
        )

        val result = kjørQuery(oppgaveQuery)
        assertThat(result.size).isEqualTo(2)

        val oppgave = hentOppgave(result[0])

        assertThat(Duration.parse(oppgave.hentVerdi("tidSidenMottattDato")).toDays()).isEqualTo(20)
    }


    private fun testdataLøpendeVarighet() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.mottattDato, LocalDateTime.now().minusDays(20).toString())
            .lagOgLagre()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.mottattDato, LocalDateTime.now().minusDays(10).toString())
            .lagOgLagre()
    }

    private fun testdataBeslutter() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.aksjonspunkt, "5016")
            .medOppgaveFeltVerdi(FeltType.løsbartAksjonspunkt, "5016")
            .lagOgLagre()

        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.aksjonspunkt, "5015")
            .medOppgaveFeltVerdi(FeltType.løsbartAksjonspunkt, "5015")
            .lagOgLagre()
    }

    private fun hentOppgave(id: Long): Oppgave {
        val oppgaveRepository = get<OppgaveRepository>()

        val oppgave = transactionalManager.transaction { tx ->
            oppgaveRepository.hentOppgaveForId(tx, id)
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

    private fun kjørQuery(oppgaveQuery: OppgaveQuery): List<Long> {
        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())
        val om = ObjectMapper().dusseldorfConfigured()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .registerKotlinModule()
        val sw = StringWriter()
        om.writeValue(sw, oppgaveQuery)

        val result = oppgaveQueryRepository.query(oppgaveQuery)
        return result
    }
}