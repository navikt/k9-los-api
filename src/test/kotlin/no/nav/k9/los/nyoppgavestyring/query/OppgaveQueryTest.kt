package no.nav.k9.los.nyoppgavestyring.query

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isNotNull
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import io.mockk.mockk
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.repository.StatistikkRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.time.LocalDate

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
                FeltverdiOppgavefilter("K9", "totrinnskontroll", "EQUALS", listOf("true")),
                FeltverdiOppgavefilter("K9", "helautomatiskBehandlet", "NOT_EQUALS", listOf("false")),
                FeltverdiOppgavefilter("K9", "mottattDato", "LESS_THAN", listOf(LocalDate.of(2022, 1, 1))),
                CombineOppgavefilter("AND", listOf(
                    FeltverdiOppgavefilter("K9", "aktorId", "GREATER_THAN_OR_EQUALS", listOf("2")),
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