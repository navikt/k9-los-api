package no.nav.k9.aksjonspunktbehandling

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.util.*
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.buildAndTestConfig
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.integrasjon.kafka.dto.PunsjEventDto
import org.intellij.lang.annotations.Language
import org.junit.Rule
import org.junit.Test
import org.koin.test.KoinTest
import org.koin.test.KoinTestRule
import org.koin.test.get
import org.slf4j.LoggerFactory
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class K9PunsjEventHandlerTest : KoinTest {

    private val log = LoggerFactory.getLogger(K9PunsjEventHandlerTest::class.java)

    @KtorExperimentalAPI
    @get:Rule
    val koinTestRule = KoinTestRule.create {
        modules(buildAndTestConfig())
    }

    @KtorExperimentalAPI
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
    }
            """.trimIndent()

        val objectMapper = jacksonObjectMapper().dusseldorfConfigured()
        val event = objectMapper.readValue(json, PunsjEventDto::class.java)

        k9PunsjEventHandler.prosesser(event)
        val oppgaveModell = oppgaveRepository.hent(UUID.fromString(event.eksternId.toString()))
        val oppgave = oppgaveModell
        assertTrue { oppgave.aktiv }
    }

    @KtorExperimentalAPI
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
}
     """.trimIndent()

        val objectMapper = jacksonObjectMapper().dusseldorfConfigured()
        val event = objectMapper.readValue(json, PunsjEventDto::class.java)

        k9PunsjEventHandler.prosesser(event)
        val oppgaveModell = oppgaveRepository.hent(UUID.fromString(event.eksternId.toString()))
        val oppgave = oppgaveModell
        assertTrue { oppgave.aktiv }
    }

    @KtorExperimentalAPI
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

        val objectMapper = jacksonObjectMapper().dusseldorfConfigured()
        val event = objectMapper.readValue(json, PunsjEventDto::class.java)

        k9PunsjEventHandler.prosesser(event)
        val oppgaveModell = oppgaveRepository.hent(UUID.fromString(event.eksternId.toString()))
        val oppgave = oppgaveModell
        assertFalse { oppgave.aktiv }
    }
}
