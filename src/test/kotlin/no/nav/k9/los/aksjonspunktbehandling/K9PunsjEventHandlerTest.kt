package no.nav.k9.los.aksjonspunktbehandling

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.integrasjon.kafka.dto.PunsjEventDto
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class K9PunsjEventHandlerTest : AbstractK9LosIntegrationTest() {

    val objectMapper = jacksonObjectMapper().dusseldorfConfigured()

    @Test
    fun `Skal opprette en oppgave dersom en punsjoppgave har et aktivt aksjonspunkt`() {

        val k9PunsjEventHandler = get<K9punsjEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()

        @Language("JSON") val json =
            """{                                                                                                                                                                                                                                                            
                "eksternId" : "9a009fb9-38ab-4bad-89e0-a3a16ecba306",                                                                                                                                                                                                                                                                                                                    
                "journalpostId" : "466988237",                                                                                                                                                                                                                                                                                                                                           
                "aktørId" : "27078522688",                                                                                                                                                                                                                                                                                                                                               
                "eventTid" : "2020-11-10T10:43:43.130644",                                                                                                                                                                                                                                                                                                                               
                "aksjonspunktKoderMedStatusListe": {
                    "PUNSJ": "OPPR"
                  }                                                                                                                                                                                                                                                                                             
            }""".trimIndent()

        val event = objectMapper.readValue(json, PunsjEventDto::class.java)

        k9PunsjEventHandler.prosesser(event)
        val oppgaveModell = oppgaveRepository.hent(UUID.fromString(event.eksternId.toString()))
        assertTrue { oppgaveModell.aktiv }
    }

    @Test
    fun `Skal håndtere at eventer har satt aktørid null`() {

        val k9PunsjEventHandler = get<K9punsjEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()

        @Language("JSON") val json =
            """{
                  "eksternId": "9a009fb9-38ab-4bad-89e0-a3a16ecba306",
                  "journalpostId": "466988237",
                  "aktørId": null,
                  "eventTid": "2020-11-10T10:43:43.130644",
                  "aksjonspunktKoderMedStatusListe": {
                    "PUNSJ": "OPPR"
                  }
               }""".trimIndent()


        val event = objectMapper.readValue(json, PunsjEventDto::class.java)

        k9PunsjEventHandler.prosesser(event)
        val oppgaveModell = oppgaveRepository.hent(UUID.fromString(event.eksternId.toString()))
        assertTrue { oppgaveModell.aktiv }
    }

    @Test
    fun `Skal avslutte oppgave dersom oppgaven ikke har noen aktive aksjonspunkter`() {

        val k9PunsjEventHandler = get<K9punsjEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()

        @Language("JSON") val json =
            """{
                  "eksternId": "9a009fb9-38ab-4bad-89e0-a3a16ecba306",
                  "journalpostId": "466988237",
                  "aktørId": "27078522688",
                  "eventTid": "2020-11-10T10:43:43.130644",
                  "aksjonspunktKoderMedStatusListe": {
                    "PUNSJ": "UTFO"
            }
        }""".trimIndent()

        val event = objectMapper.readValue(json, PunsjEventDto::class.java)

        k9PunsjEventHandler.prosesser(event)
        val oppgaveModell = oppgaveRepository.hent(UUID.fromString(event.eksternId.toString()))
        assertFalse { oppgaveModell.aktiv }
    }
}
