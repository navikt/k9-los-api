package no.nav.k9.los.nyoppgavestyring.query

import assertk.assertThat
import assertk.assertions.isEmpty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import org.junit.jupiter.api.Test
import java.io.StringWriter
import java.time.LocalDate

class OppgaveQueryTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `sjekker at oppgave-query kan kjøres mot database`() {
        val oppgaveQueryRepository = OppgaveQueryRepository(dataSource)
        val oppgaveQuery = OppgaveQuery(listOf(
            FeltverdiOppgavefilter(null, "oppgavestatus", "EQUALS", "OPPR"),
            FeltverdiOppgavefilter(null, "kildeområde", "EQUALS", "K9"),
            FeltverdiOppgavefilter(null, "oppgavetype", "EQUALS", "aksjonspunkt"),
            FeltverdiOppgavefilter(null, "oppgaveområde", "EQUALS", "aksjonspunkt"),
            FeltverdiOppgavefilter("K9", "fagsystem", "NOT_EQUALS", "Tullball"),
            CombineOppgavefilter("OR", listOf(
                FeltverdiOppgavefilter("K9", "totrinnskontroll", "EQUALS", "true"),
                FeltverdiOppgavefilter("K9", "helautomatiskBehandlet", "NOT_EQUALS", "false"),
                FeltverdiOppgavefilter("K9", "mottattDato", "LESS_THAN", LocalDate.of(2022, 1, 1)),
                CombineOppgavefilter("AND", listOf(
                    FeltverdiOppgavefilter("K9", "aktorId", "GREATER_THAN_OR_EQUALS", "2"),
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

}