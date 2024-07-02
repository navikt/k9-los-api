package no.nav.k9.los.aksjonspunktbehandling

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEqualTo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.Oppgave
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class K9PunsjEventHandlerTest : AbstractK9LosIntegrationTest() {

    val objectMapper = jacksonObjectMapper().dusseldorfConfigured()
    private lateinit var k9PunsjEventHandler: K9punsjEventHandler
    private lateinit var oppgaveRepository: OppgaveRepository
    private lateinit var oppgaveV3Repository: no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
    private lateinit var transactionalManager: TransactionalManager


    @BeforeEach
    fun setup() {
        val områdeSetup = get<OmrådeSetup>()
        områdeSetup.setup()
        val k9PunsjTilLosAdapterTjeneste = get<K9PunsjTilLosAdapterTjeneste>()
        k9PunsjTilLosAdapterTjeneste.setup()
        k9PunsjEventHandler = get<K9punsjEventHandler>()
        oppgaveRepository = get<OppgaveRepository>()
        oppgaveV3Repository = get<no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository>()
        transactionalManager = get<TransactionalManager>()
    }

    @Test
    fun `Skal opprette en oppgave dersom en punsjoppgave har et aktivt aksjonspunkt`() {

        val eksternId = "9a009fb9-38ab-4bad-89e0-a3a16ecba306"
        val eventTid = "2020-11-10T10:43:43.130644"
        val aktørId = "27078522688"

        @Language("JSON") val json =
            """{                                                                                                                                                                                                                                                            
                "eksternId" : "$eksternId",                                                                                                                                                                                                                                                                                                                    
                "journalpostId" : "466988237",                                                                                                                                                                                                                                                                                                                                           
                "aktørId" : "$aktørId",                                                                                                                                                                                                                                                                                                                                               
                "eventTid" : "$eventTid",                                                                                                                                                                                                                                                                                                                               
                "journalførtTidspunkt": "$eventTid", 
                "aksjonspunktKoderMedStatusListe": {
                    "PUNSJ": "OPPR"
                  }                                                                                                                                                                                                                                                                                             
            }""".trimIndent()

        val event = objectMapper.readValue(json, PunsjEventDto::class.java)

        k9PunsjEventHandler.prosesser(event)
        val oppgaveModell = oppgaveRepository.hent(UUID.fromString(event.eksternId.toString()))
        assertTrue { oppgaveModell.aktiv }

        val oppgaveV3 = hentV3Oppgave(event)

        val felter = oppgaveV3.felter.sortedBy { it.eksternId }
        assertThat(felter).hasSize(4)
        assertThat(felter[0].eksternId).isEqualTo("aktorId")
        assertThat(felter[0].verdi).isEqualTo(aktørId)
        assertThat(felter[1].eksternId).isEqualTo("journalfort")
        assertThat(felter[1].verdi).isEqualTo("true")
        assertThat(felter[2].eksternId).isEqualTo("journalfortTidspunkt")
        assertThat(felter[2].verdi).isEqualTo(eventTid)
        assertThat(felter[3].eksternId).isEqualTo("registrertDato")
        assertThat(felter[3].verdi).isEqualTo(eventTid)
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

        // V1
        val oppgaveModell = oppgaveRepository.hent(UUID.fromString(event.eksternId.toString()))
        assertTrue { oppgaveModell.aktiv }

        // V3
        val oppgaveV3 = hentV3Oppgave(event)
        assertThat(oppgaveV3.status).isEqualTo(Oppgavestatus.AAPEN.kode)
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

        // V1
        val oppgaveModell = oppgaveRepository.hent(UUID.fromString(event.eksternId.toString()))
        assertFalse { oppgaveModell.aktiv }

        // V3
        val oppgaveV3 = hentV3Oppgave(event)
        // TODO: Skal status på oppgaven endres her, eller er testen ikke relevant?
//        assertThat(oppgaveV3.status).isNotEqualTo(Oppgavestatus.AAPEN.kode)
    }

    @Test
    fun `Skal opprette oppgave med journalførtTidspunkt satt på eventet`() {

        val k9PunsjEventHandler = get<K9punsjEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()

        @Language("JSON") val json =
            """{
                  "eksternId": "9a009fb9-38ab-4bad-89e0-a3a16ecba306",
                  "journalpostId": "466988237",
                  "aktørId": "27078522688",
                  "eventTid": "2020-11-10T10:43:43.130644",
                  "aksjonspunktKoderMedStatusListe": {
                    "PUNSJ": "OPPR"
                  },
                  "journalførtTidspunkt": "2020-11-10T10:43:43.130644"
            }""".trimIndent()

        val event = objectMapper.readValue(json, PunsjEventDto::class.java)
        k9PunsjEventHandler.prosesser(event)

        // V1
        val oppgaveModell = oppgaveRepository.hent(UUID.fromString(event.eksternId.toString()))
        assertTrue { oppgaveModell.journalførtTidspunkt != null }

        // V3
        val oppgaveV3 = hentV3Oppgave(event)
        assertThat(oppgaveV3.felter.find { it.eksternId == "journalfort" }!!.verdi).isEqualTo("true")
    }

    private fun hentV3Oppgave(event: PunsjEventDto): Oppgave {
        return transactionalManager.transaction { tx ->
            oppgaveV3Repository.hentNyesteOppgaveForEksternId(
                tx,
                "K9",
                event.eksternId.toString()
            )
        }
    }
}
