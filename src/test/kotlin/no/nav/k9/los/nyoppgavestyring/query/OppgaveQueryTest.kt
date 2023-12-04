package no.nav.k9.los.nyoppgavestyring.query

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotNull
import assertk.assertions.isNotEmpty
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.mockk
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.query.db.FeltverdiOperator
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.time.LocalDate
import java.util.*

class OppgaveQueryTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `sjekker at oppgave-query kan kjøres mot database`() {
        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())
        val oppgaveQuery = OppgaveQuery(listOf(
            FeltverdiOppgavefilter(null, "oppgavestatus", "EQUALS", listOf("OPPR")),
            FeltverdiOppgavefilter(null, "kildeområde", "EQUALS", listOf("K9")),
            FeltverdiOppgavefilter(null, "oppgavetype", "EQUALS", listOf("aksjonspunkt")),
            FeltverdiOppgavefilter(null, "oppgaveområde", "EQUALS", listOf("aksjonspunkt")),
            FeltverdiOppgavefilter("K9", "fagsystem", "NOT_EQUALS", listOf("Tullball")),
            CombineOppgavefilter("OR", listOf(
                FeltverdiOppgavefilter("K9", "helautomatiskBehandlet", "NOT_EQUALS", listOf("false")),
                FeltverdiOppgavefilter("K9", "mottattDato", "LESS_THAN", listOf(LocalDate.of(2022, 1, 1))),
                CombineOppgavefilter("AND", listOf(
                    FeltverdiOppgavefilter("K9", "totrinnskontroll", "EQUALS", listOf("true")),
                ))
            ))
        ))

        // Verifiserer at det ikke blir exceptions ved serialisering + deserialisering:
        val om = ObjectMapper().dusseldorfConfigured()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .registerKotlinModule()
        val sw = StringWriter()
        om.writeValue(sw, oppgaveQuery)
        val json = sw.toString()
        val objectAgain: OppgaveQuery = om.readValue(json, OppgaveQuery::class.java)

        val result = oppgaveQueryRepository.query(oppgaveQuery)
        assertThat(result).isEmpty()
    }

    @Test
    fun `sjekker at oppgave-query kan utvides ved flere verdier i filter`() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.aksjonspunkt, "5016")
            .lagOgLagre()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())
        val oppgaveQuery = OppgaveQuery(listOf(
            byggFilterK9(FeltType.aksjonspunkt, FeltverdiOperator.EQUALS, "5016")
        ))

        val om = ObjectMapper().dusseldorfConfigured()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .registerKotlinModule()
        val sw = StringWriter()
        om.writeValue(sw, oppgaveQuery)

        val result = oppgaveQueryRepository.query(oppgaveQuery)
        assertThat(result).isNotEmpty()
    }

    @Test
    fun `sjekker at oppgave-query kan sammenligne timestamp`() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.mottattDato, "2023-05-15T00:00:00.000")
            .lagOgLagre()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.mottattDato, FeltverdiOperator.GREATER_THAN, "2023-05-14T00:00:00.000"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.mottattDato, FeltverdiOperator.LESS_THAN, "2023-05-15T00:00:00.000"),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.mottattDato, FeltverdiOperator.GREATER_THAN, "2023-05-15T00:00:00.000"),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.mottattDato, FeltverdiOperator.LESS_THAN, "2023-05-16T00:00:00.000"),
        )))).isNotEmpty()
    }

    @Test
    fun `sjekker at oppgave-query kan sjekke timestamp for likhet - urealistisk med timestamp`() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.mottattDato, "2023-05-15T00:00:00.000")
            .lagOgLagre()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.mottattDato, FeltverdiOperator.LESS_THAN_OR_EQUALS, "2023-05-15T00:00:00.000"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.mottattDato, FeltverdiOperator.EQUALS, "2023-05-15T00:00:00.000"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.mottattDato, FeltverdiOperator.GREATER_THAN_OR_EQUALS, "2023-05-15T00:00:00.000"),
        )))).isNotEmpty()
    }


    @Test
    fun `sjekker at oppgave-query med kun dato kan sjekkes mot timestamp`() {
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.mottattDato, "2023-05-15T00:00:00.000")
            .lagOgLagre()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.mottattDato, FeltverdiOperator.LESS_THAN_OR_EQUALS, "2023-05-16"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.mottattDato, FeltverdiOperator.EQUALS, "2023-05-15"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.mottattDato, FeltverdiOperator.GREATER_THAN_OR_EQUALS, "2023-05-14"),
        )))).isNotEmpty()
    }

    @Test
    fun `sjekker at oppgave-query med boolean kan ha null`() {
        val behandlingUuid = UUID.randomUUID().toString()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.behandlingUuid, behandlingUuid)
            .lagOgLagre()   // avventerArbeidsgiver er null

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.avventerArbeidsgiver, FeltverdiOperator.EQUALS, null),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.avventerArbeidsgiver, FeltverdiOperator.IN, null),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.avventerArbeidsgiver, FeltverdiOperator.IN, null, "true"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.avventerArbeidsgiver, FeltverdiOperator.IN, "true"),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.avventerArbeidsgiver, FeltverdiOperator.EQUALS, "true"),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.avventerArbeidsgiver, FeltverdiOperator.EQUALS, "false"),
        )))).isEmpty()
    }

    @Test
    fun `sjekker at oppgave-query med boolean med true`() {
        val behandlingUuid = UUID.randomUUID().toString()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.behandlingUuid, behandlingUuid)
            .medOppgaveFeltVerdi(FeltType.avventerArbeidsgiver, "true")
            .lagOgLagre()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.avventerArbeidsgiver, FeltverdiOperator.EQUALS, null),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.avventerArbeidsgiver, FeltverdiOperator.IN, null),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.avventerArbeidsgiver, FeltverdiOperator.IN, null, "true"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.avventerArbeidsgiver, FeltverdiOperator.EQUALS, "true"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.avventerArbeidsgiver, FeltverdiOperator.IN, "true"),
        )))).isNotEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.avventerArbeidsgiver, FeltverdiOperator.EQUALS, "false"),
        )))).isEmpty()
    }

    @Test
    fun `sjekker at oppgave-query med flere datoer som ikke skal matche`() {
        val behandlingUuid = UUID.randomUUID().toString()
        OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.behandlingUuid, behandlingUuid)
            .medOppgaveFeltVerdi(FeltType.mottattDato, "2023-05-15T12:00:00.000")
            .lagOgLagre()

        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource, mockk<FeltdefinisjonRepository>())

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.mottattDato, FeltverdiOperator.NOT_EQUALS, "2023-05-15T00:00:00.000"),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.mottattDato, FeltverdiOperator.NOT_EQUALS, "2023-05-15T00:00:00.000", "2023-05-16T00:00:00.000"),
        )))).isEmpty()

        assertThat(oppgaveQueryRepository.query(OppgaveQuery(listOf(
            byggFilterK9(FeltType.behandlingUuid, FeltverdiOperator.EQUALS, behandlingUuid),
            byggFilterK9(FeltType.mottattDato, FeltverdiOperator.NOT_EQUALS, "2023-05-14T00:00:00.000", "2023-05-16T00:00:00.000"),
        )))).isNotEmpty()
    }


    private fun byggFilterK9(feltType: FeltType, feltverdiOperator: FeltverdiOperator, vararg verdier: String?): FeltverdiOppgavefilter {
        return FeltverdiOppgavefilter(
            "K9",
            feltType.eksternId,
            feltverdiOperator.name,
            verdier.toList()
        )
    }

    @Test
    fun `sjekker at oppgave-query kan deserialiseres med feltverdi som ikke er array`() {
        val json = """
            {
              "filtere" : [{
                "type" : "feltverdi",
                "område" : null,
                "kode" : "oppgavestatus",
                "operator" : "EQUALS",
                "verdi" : "OPPR"
              }],
              "select" : [ ],
              "order" : [ ],
              "limit" : 10
            }
        """.trimIndent()

        val om = ObjectMapper().dusseldorfConfigured()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .registerKotlinModule()

        val objectAgain: OppgaveQuery = om.readValue(json, OppgaveQuery::class.java)
        assertThat(objectAgain).isNotNull()
    }

}