package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotliquery.sessionOf
import kotliquery.using
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Repository
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class K9PunsjEventhandlerTest : AbstractK9LosIntegrationTest() {

    val objectMapper = jacksonObjectMapper().dusseldorfConfigured()

    @BeforeEach
    fun setup() {
        val områdeSetup = get<OmrådeSetup>()
        områdeSetup.setup()
        val k9PunsjTilLosAdapterTjeneste = get<K9PunsjTilLosAdapterTjeneste>()
        k9PunsjTilLosAdapterTjeneste.setup()
    }

    @Test
    fun `Skal opprette en oppgave dersom en punsjoppgave har et aktivt aksjonspunkt`() {

        val k9PunsjEventHandler = get<K9PunsjEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()

        val eksternId = "9a009fb9-38ab-4bad-89e0-a3a16ecba306"
        val eventTid = "2020-11-10T10:43:43.130644"
        val aktørId = "27078522688"
        val journalpostId = "466988237"

        @Language("JSON") val json =
            """{                                                                                                                                                                                                                                                            
                "eksternId" : "$eksternId",                                                                                                                                                                                                                                                                                                                    
                "journalpostId" : "$journalpostId",                                                                                                                                                                                                                                                                                                                                           
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

        val områdeRepository = get<OmrådeRepository>()
        val oppgaveV3Repository = get<no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository>()
        val transactionalManager = get<TransactionalManager>()

        val oppgaveV3 = transactionalManager.transaction { tx ->
            oppgaveV3Repository.hentNyesteOppgaveForEksternId(tx, "K9", event.eksternId.toString())
        }

        val felter = oppgaveV3.felter.sortedBy { it.eksternId }
        assertThat(felter).hasSize(9)
        assertThat(felter[0].eksternId).isEqualTo("aktorId")
        assertThat(felter[0].verdi).isEqualTo(aktørId)
        assertThat(felter[1].eksternId).isEqualTo("behandlingTypekode")
        assertThat(felter[1].verdi).isEqualTo("UKJENT")
        assertThat(felter[2].eksternId).isEqualTo("helautomatiskBehandlet")
        assertThat(felter[2].verdi).isEqualTo("false")
        assertThat(felter[3].eksternId).isEqualTo("journalfort")
        assertThat(felter[3].verdi).isEqualTo("true")
        assertThat(felter[4].eksternId).isEqualTo("journalfortTidspunkt")
        assertThat(felter[4].verdi).isEqualTo(eventTid)
        assertThat(felter[5].eksternId).isEqualTo("journalpostId")
        assertThat(felter[5].verdi).isEqualTo(journalpostId)
        assertThat(felter[6].eksternId).isEqualTo("mottattDato")
        assertThat(felter[6].verdi).isEqualTo(eventTid)
        assertThat(felter[7].eksternId).isEqualTo("registrertDato")
        assertThat(felter[7].verdi).isEqualTo(eventTid)
        assertThat(felter[8].eksternId).isEqualTo("ytelsestype")
        assertThat(felter[8].verdi).isEqualTo("UKJENT")

    }

    @Test
    fun `Skal håndtere at eventer har satt aktørid null`() {

        val k9PunsjEventHandler = get<K9PunsjEventHandler>()
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

        val k9PunsjEventHandler = get<K9PunsjEventHandler>()
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

    @Test
    fun `Skal opprette oppgave med journalførtTidspunkt satt på eventet`() {

        val k9PunsjEventHandler = get<K9PunsjEventHandler>()
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
        val oppgaveModell = oppgaveRepository.hent(UUID.fromString(event.eksternId.toString()))
        assertTrue { oppgaveModell.journalførtTidspunkt != null }
    }

    @Test
    fun `Skal opprette oppgave med journalførtTidspunkt satt på eventet, og så uten`() {

        val k9PunsjEventHandler = get<K9PunsjEventHandler>()
        val oppgaveV3Repository = get<OppgaveV3Repository>()
        val oppgavetypeRepository = get<OppgavetypeRepository>()

        @Language("JSON") val json1 =
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

        val event1 = objectMapper.readValue(json1, PunsjEventDto::class.java)

        @Language("JSON") val json2 =
            """{
                  "eksternId": "9a009fb9-38ab-4bad-89e0-a3a16ecba306",
                  "journalpostId": "466988237",
                  "aktørId": "27078522688",
                  "eventTid": "2020-11-10T10:43:44.130644",
                  "aksjonspunktKoderMedStatusListe": {
                    "PUNSJ": "OPPR",
                    "MER_INFORMASJON": "OPPR"
                  },
                  "journalførtTidspunkt": null
            }""".trimIndent()

        val event2 = objectMapper.readValue(json2, PunsjEventDto::class.java)

        k9PunsjEventHandler.prosesser(event1)
        k9PunsjEventHandler.prosesser(event2)

        return using(sessionOf(dataSource)) { it ->
            it.transaction { tx ->
                val oppgavetype = oppgavetypeRepository.hentOppgavetype("K9", "k9punsj")
                val oppgaveV3 = oppgaveV3Repository.hentAktivOppgave(event1.eksternId.toString(), oppgavetype, tx)
                assertEquals(oppgaveV3?.hentVerdi("journalfort"), "true")
                assertEquals(oppgaveV3?.hentVerdi("journalfortTidspunkt"), "2020-11-10T10:43:43.130644")
            }
        }
    }
}