package no.nav.k9.los.aksjonspunktbehandling

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.ktor.jackson.dusseldorfConfigured
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.modell.Enhet
import no.nav.k9.los.domene.modell.KøSortering
import no.nav.k9.los.domene.modell.OppgaveKø
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class K9sakEventHandlerTest : AbstractK9LosIntegrationTest() {

    val objectMapper = jacksonObjectMapper()
        .dusseldorfConfigured().setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)

    @BeforeEach
    fun setup() {
        val områdeSetup = get<OmrådeSetup>()
        områdeSetup.setup()
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()
        k9SakTilLosAdapterTjeneste.setup()
    }

    @Test
    fun `Skal lukke oppgave dersom den ikke har noen aktive aksjonspunkter`() {

        val k9sakEventHandler = get<K9sakEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()

        @Language("JSON") val json =
            """{
                  "eksternId": "70c7a780-08ad-4ccf-8cef-c341d4913d65",
                  "fagsystem": {
                    "kode": "K9SAK",
                    "kodeverk": "FAGSYSTEM"
                  },
                  "saksnummer": "5YC1S",
                  "aktørId": "9916107629061",
                  "behandlingId": 999951,
                   "behandlingstidFrist": "2020-03-31",
                  "eventTid": "2020-03-31T06:33:59.460931",
                  "eventHendelse": "BEHANDLINGSKONTROLL_EVENT",
                  "behandlinStatus": "UTRED",
                  "behandlingStatus": null,
                  "behandlingSteg": "INREG",
                  "behandlendeEnhet": null,
                  "ansvarligBeslutterForTotrinn": null,
                  "ansvarligSaksbehandlerForTotrinn": null,
                  "ytelseTypeKode": "OMP",
                  "søknadsårsaker": [
                    {
                      "kode": "KONFLIKT_MED_ARBEIDSGIVER",
                      "navn": "Konflikt med arbeidsgiver"
                    }],
                  "behandlingTypeKode": "BT-002",
                  "opprettetBehandling": "2020-03-31T06:33:48",
                  "aksjonspunktKoderMedStatusListe": {}
                }
            """.trimIndent()

        val event = objectMapper.readValue(json, BehandlingProsessEventDto::class.java)

        k9sakEventHandler.prosesser(event)
        val oppgaveModell = oppgaveRepository.hent(UUID.fromString(event.eksternId.toString()))
        assertFalse { oppgaveModell.aktiv }
    }

    @Test
    fun `Skal lukke oppgave dersom den er satt på vent`() {
        val k9sakEventHandler = get<K9sakEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()


        @Language("JSON") val json =
            """{
              "eksternId": "6b521f78-ef71-43c3-a615-6c2b8bb4dcdb",
              "fagsystem": {
                "kode": "K9SAK",
                "kodeverk": "FAGSYSTEM"
              },
              "saksnummer": "5YC4K",
              "aktørId": "9906098522415",
              "behandlingId": 1000001,
              "eventTid": "2020-02-20T07:38:49",
              "eventHendelse": "BEHANDLINGSKONTROLL_EVENT",
              "behandlinStatus": "UTRED",
               "behandlingstidFrist": "2020-03-31",
              "behandlingStatus": "UTRED",
              "behandlingSteg": "INREG_AVSL",
              "behandlendeEnhet": "0300",
              "ytelseTypeKode": "PSB",
              "behandlingTypeKode": "BT-002",
              "opprettetBehandling": "2020-02-20T07:38:49",
              "søknadsårsaker": [],
              "aksjonspunktKoderMedStatusListe": {
                "7030": "OPPR"
              }
            }"""

        val event = objectMapper.readValue(json, BehandlingProsessEventDto::class.java)

        k9sakEventHandler.prosesser(event)
        val oppgave =
            oppgaveRepository.hent(UUID.fromString("6b521f78-ef71-43c3-a615-6c2b8bb4dcdb"))
        assertFalse { oppgave.aktiv }
    }

    @Test
    fun `Skal i beslutter kø bare dersom beslutter aksjonspunktet forekommer alene`() {
        val k9sakEventHandler = get<K9sakEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()


        @Language("JSON") val json =
            """{
              "eksternId": "6b521f78-ef71-43c3-a615-6c2b8bb4dcdb",
              "fagsystem": {
                "kode": "K9SAK",
                "kodeverk": "FAGSYSTEM"
              },
              "saksnummer": "5YC4K",
              "aktørId": "9906098522415",
              "behandlingId": 1000001,
              "eventTid": "2020-02-20T07:38:49",
              "eventHendelse": "BEHANDLINGSKONTROLL_EVENT",
              "behandlinStatus": "UTRED",
               "behandlingstidFrist": "2020-03-31",
              "behandlingStatus": "UTRED",
              "behandlingSteg": "INREG_AVSL",
              "behandlendeEnhet": "0300",
              "ytelseTypeKode": "PSB",
              "behandlingTypeKode": "BT-002",
              "opprettetBehandling": "2020-02-20T07:38:49",
              "aksjonspunktKoderMedStatusListe": {
                "5016": "OPPR",
                "5080": "OPPR"
              }
            }"""
        val event = objectMapper.readValue(json, BehandlingProsessEventDto::class.java)

        k9sakEventHandler.prosesser(event)
        val oppgave =
            oppgaveRepository.hent(UUID.fromString("6b521f78-ef71-43c3-a615-6c2b8bb4dcdb"))
        assertFalse { oppgave.tilBeslutter }
    }

    @Test
    fun `Skal i beslutter kø bare dersom beslutter aksjonspunktet forekommer alene positiv test`() {
        val k9sakEventHandler = get<K9sakEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()


        @Language("JSON") val json =
            """{
              "eksternId": "6b521f78-ef71-43c3-a615-6c2b8bb4dcdb",
              "fagsystem": {
                "kode": "K9SAK",
                "kodeverk": "FAGSYSTEM"
              },
              "fagsakPeriode" : {
                    "fom" : "2020-02-20",
                    "tom" : "2020-03-30"
                  },
              "saksnummer": "5YC4K",
              "aktørId": "9906098522415",
              "behandlingId": 1000001,
              "eventTid": "2020-02-20T07:38:49",
              "eventHendelse": "BEHANDLINGSKONTROLL_EVENT",
              "behandlinStatus": "UTRED",
               "behandlingstidFrist": "2020-03-31",
              "behandlingStatus": "UTRED",
              "behandlingSteg": "INREG_AVSL",
              "behandlendeEnhet": "0300",
              "ytelseTypeKode": "PSB",
              "behandlingTypeKode": "BT-002",
              "opprettetBehandling": "2020-02-20T07:38:49",
              "aksjonspunktKoderMedStatusListe": {
                "5016": "OPPR"
              }
            }"""
        val event = objectMapper.readValue(json, BehandlingProsessEventDto::class.java)

        k9sakEventHandler.prosesser(event)
        val oppgave =
            oppgaveRepository.hent(UUID.fromString("6b521f78-ef71-43c3-a615-6c2b8bb4dcdb"))
        assertTrue { oppgave.tilBeslutter }
    }

    @Test
    fun `Skal støtte nytt format`() {


        @Language("JSON") val json =
            """{
                  "eksternId": "6b521f78-ef71-43c3-a615-6c2b8bb4dcdb",
                  "fagsystem": {
                    "kode": "K9SAK",
                    "kodeverk": "FAGSYSTEM"
                  },
                  "saksnummer": "5YC4K",
                  "aktørId": "9906098522415",
                  "behandlingId": 1000001,
                  "eventTid": "2020-02-20T07:38:49",
                  "eventHendelse": "BEHANDLINGSKONTROLL_EVENT",
                  "behandlinStatus": "UTRED",
                   "behandlingstidFrist": "2020-03-31",
                  "behandlingStatus": "UTRED",
                  "behandlingSteg": "INREG_AVSL",
                  "behandlendeEnhet": "0300",
                  "ytelseTypeKode": "OMP",
                  "behandlingTypeKode": "BT-002",
                  "opprettetBehandling": "2020-02-20T07:38:49",
                  "aksjonspunktKoderMedStatusListe": {
                    "5009": "OPPR"
                  },
                  "fagsakPeriode" : {
                    "fom" : "2020-02-20",
                    "tom" : "2020-03-30"
                  },
                  "pleietrengendeAktørId" : "9906098522415",
                  "relatertPartAktørId" : "9906098522415"
                  
                }"""
        val event = objectMapper.readValue(json, BehandlingProsessEventDto::class.java)

        assertThat(event.fagsakPeriode?.fom).isEqualTo(LocalDate.of(2020, 2, 20))
        assertThat(event.fagsakPeriode?.tom).isEqualTo(LocalDate.of(2020, 3, 30))
    }

    @Test
    fun `Skal opprette oppgave dersom 5009`() {
        val k9sakEventHandler = get<K9sakEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()


        @Language("JSON") val json =
            """{
                  "eksternId": "6b521f78-ef71-43c3-a615-6c2b8bb4dcdb",
                  "fagsystem": {
                    "kode": "K9SAK",
                    "kodeverk": "FAGSYSTEM"
                  },
                  "saksnummer": "5YC4K",
                  "aktørId": "9906098522415",
                  "behandlingId": 1000001,
                  "eventTid": "2020-02-20T07:38:49",
                  "eventHendelse": "BEHANDLINGSKONTROLL_EVENT",
                  "behandlinStatus": "UTRED",
                   "behandlingstidFrist": "2020-03-31",
                  "behandlingStatus": "UTRED",
                  "behandlingSteg": "INREG_AVSL",
                  "behandlendeEnhet": "0300",
                  "ytelseTypeKode": "OMP",
                  "behandlingTypeKode": "BT-002",
                  "opprettetBehandling": "2020-02-20T07:38:49",
                  "aksjonspunktKoderMedStatusListe": {
                    "5009": "OPPR"
                  }
                }"""

        val event = objectMapper.readValue(json, BehandlingProsessEventDto::class.java)

        k9sakEventHandler.prosesser(event)
        val oppgave =
            oppgaveRepository.hent(UUID.fromString("6b521f78-ef71-43c3-a615-6c2b8bb4dcdb"))
        assertTrue { oppgave.aktiv }
    }

    @Test
    fun `Skal ha 1 oppgave med 3 aksjonspunkter`() {
        val k9sakEventHandler = get<K9sakEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()

        @Language("JSON") val json =
            """{
                  "eksternId": "6b521f78-ef71-43c3-a615-6c2b8bb4dcdb",
                  "fagsystem": {
                    "kode": "K9SAK",
                    "kodeverk": "FAGSYSTEM"
                  },
                  "saksnummer": "5YC4K",
                  "aktørId": "9906098522415",
                  "behandlingId": 1000001,
                  "eventTid": "2020-02-20T07:38:49",
                  "eventHendelse": "BEHANDLINGSKONTROLL_EVENT",
                  "behandlinStatus": "UTRED",
                   "behandlingstidFrist": "2020-03-31",
                  "behandlingStatus": "UTRED",
                  "behandlingSteg": "INREG_AVSL",
                  "behandlendeEnhet": "0300",
                  "ytelseTypeKode": "OMP",
                  "behandlingTypeKode": "BT-002",
                  "opprettetBehandling": "2020-02-20T07:38:49",
                  "aksjonspunktKoderMedStatusListe": {
                    "5009": "OPPR",
                    "5084": "OPPR",
                    "5080": "OPPR"
                  }
                }"""
        val event = objectMapper.readValue(json, BehandlingProsessEventDto::class.java)

        k9sakEventHandler.prosesser(event)
        val oppgave =
            oppgaveRepository.hent(UUID.fromString("6b521f78-ef71-43c3-a615-6c2b8bb4dcdb"))
        assertTrue { oppgave.aktiv }
        assertTrue(oppgave.aksjonspunkter.hentLengde() == 3)
    }

    @Test
    fun `Støtte tilbakekreving`() {
        val k9TilbakeEventHandler = get<K9TilbakeEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()


        @Language("JSON") val json =
            """{
               "eksternId": "5c7be441-ebf3-4878-9ebc-399635b0a179",
               "fagsystem": "K9TILBAKE",
               "saksnummer": "61613602",
               "aktørId": "9914079721604",
               "behandlingId": null,
               "eventTid": "2020-06-17T13:07:15.674343500",
               "eventHendelse": "AKSJONSPUNKT_OPPRETTET",
               "behandlinStatus": null,
               "behandlingStatus": "UTRED",
               "behandlingSteg": "FAKTFEILUTSTEG",
               "behandlendeEnhet": "4833",
               "ytelseTypeKode": "OMP",
               "behandlingTypeKode": "BT-007",
               "opprettetBehandling": "2020-06-16T13:16:51.690",
               "aksjonspunktKoderMedStatusListe": {
                               "5030": "UTFO",
                               "7002": "UTFO",
                               "7001": "OPPR",
                               "7003": "OPPR"
               },
               "href": "/fpsak/fagsak/61613602/behandling/53/?punkt=default&fakta=default",
               "førsteFeilutbetaling": "2019-10-19",
               "feilutbetaltBeløp": 26820,
               "ansvarligSaksbehandlerIdent": "saksbeh"
}       """

        val event = AksjonspunktLagetTilbake().deserialize(null, json.toByteArray())!!

        k9TilbakeEventHandler.prosesser(event)
        val oppgave =
            oppgaveRepository.hent(UUID.fromString("5c7be441-ebf3-4878-9ebc-399635b0a179"))
        assertTrue { !oppgave.aktiv }
    }

    @Test
    fun `Oppgave skal ende opp i kø`() {
        val k9sakEventHandler = get<K9sakEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()

        @Language("JSON") val json =
            """{
                  "eksternId": "6b521f78-ef71-43c3-a615-6c2b8bb4dcdb",
                  "fagsystem": {
                    "kode": "K9SAK",
                    "kodeverk": "FAGSYSTEM"
                  },
                  "saksnummer": "5YC4K",
                  "aktørId": "9906098522415",
                  "behandlingId": 1000001,
                  "eventTid": "2020-02-20T07:38:49",
                  "eventHendelse": "BEHANDLINGSKONTROLL_EVENT",
                  "behandlinStatus": "UTRED",
                   "behandlingstidFrist": "2020-03-31",
                  "behandlingStatus": "UTRED",
                  "behandlingSteg": "INREG_AVSL",
                  "behandlendeEnhet": "0300",
                  "ytelseTypeKode": "OMP",
                  "behandlingTypeKode": "BT-002",
                  "opprettetBehandling": "2020-02-20T07:38:49",
                  "aksjonspunktKoderMedStatusListe": {
                    "5009": "OPPR"
                  }
                }"""

        val event = objectMapper.readValue(json, BehandlingProsessEventDto::class.java)

        val oppgaveKøRepository = get<OppgaveKøRepository>()

        runBlocking {
            val uuid = UUID.randomUUID()
            oppgaveKøRepository.lagre(uuid) {
                OppgaveKø(
                    id = uuid,
                    navn = "Alle",
                    sistEndret = LocalDate.now(),
                    sortering = KøSortering.OPPRETT_BEHANDLING,
                    filtreringBehandlingTyper = arrayListOf(),
                    filtreringYtelseTyper = arrayListOf(),
                    filtreringAndreKriterierType = arrayListOf(),
                    enhet = Enhet.NASJONAL,
                    fomDato = null,
                    tomDato = null,
                    saksbehandlere = arrayListOf(),
                    skjermet = false,
                    oppgaverOgDatoer = mutableListOf(),
                    kode6 = false
                )
            }
        }
        k9sakEventHandler.prosesser(event)
        val oppgave =
            oppgaveRepository.hent(UUID.fromString("6b521f78-ef71-43c3-a615-6c2b8bb4dcdb"))
        assertTrue { oppgave.aktiv }
        assertTrue(oppgave.aksjonspunkter.hentLengde() == 1)

        val i = runBlocking {
            oppgaveKøRepository.hent()
        }
        assertSame(1, i[0].oppgaverOgDatoer.size)
    }

    @Test
    fun `Vaskeevent skal ignoreres hvis det allerede finnes event med avsluttet behandling`() {
        val k9sakEventHandler = get<K9sakEventHandler>()

        val eksternId = UUID.randomUUID()
        val eventTid = LocalDateTime.now().minusDays(1)
        k9sakEventHandler.prosesser(BehandlingProsessEventDtoBuilder(eksternId).opprettet().apply { this.eventTid = eventTid }.build())
        k9sakEventHandler.prosesser(BehandlingProsessEventDtoBuilder(eksternId).foreslåVedtak().apply { this.eventTid = eventTid.plusHours(1) }.build())
        k9sakEventHandler.prosesser(BehandlingProsessEventDtoBuilder(eksternId).iverksettVedtak().apply { this.eventTid = eventTid.plusHours(2) }.build())

        val vaskeevent = BehandlingProsessEventDtoBuilder(eksternId).iverksettVedtak().apply {
            this.eventTid = LocalDateTime.now()
            this.eventHendelse = EventHendelse.VASKEEVENT
        }.build()
        assertThat(k9sakEventHandler.håndterVaskeevent(vaskeevent)).isNull()
    }

    @Test
    fun `Vaskeevent skal brukes hvis det ikke finnes event med avsluttet behandling`() {
        val k9sakEventHandler = get<K9sakEventHandler>()

        val eksternId = UUID.randomUUID()
        val eventTid = LocalDateTime.now().minusDays(1)
        k9sakEventHandler.prosesser(BehandlingProsessEventDtoBuilder(eksternId).opprettet().apply { this.eventTid = eventTid }.build())
        k9sakEventHandler.prosesser(BehandlingProsessEventDtoBuilder(eksternId).foreslåVedtak().apply { this.eventTid = eventTid.plusHours(1) }.build())

        val vaskeevent = BehandlingProsessEventDtoBuilder(eksternId).iverksettVedtak().apply {
            this.eventTid = eventTid.plusHours(2)
            this.eventHendelse = EventHendelse.VASKEEVENT
        }.build()

        val håndtertEvent = k9sakEventHandler.håndterVaskeevent(vaskeevent)
        
        assertThat(håndtertEvent).isNotNull()
        assertThat(håndtertEvent!!.eventTid).isEqualTo(eventTid.plusHours(2))
    }

    @Test
    fun `Vaskeevent skal brukes hvis det ikke finnes event med avsluttet behandling, og utsetter ikke eventtid eventtid 100 mikrosekunder hvis tidligere eller lik siste event`() {
        val k9sakEventHandler = get<K9sakEventHandler>()

        val eksternId = UUID.randomUUID()
        val eventTid = LocalDateTime.now().minusDays(1)
        k9sakEventHandler.prosesser(BehandlingProsessEventDtoBuilder(eksternId).opprettet().apply { this.eventTid = eventTid }.build())
        k9sakEventHandler.prosesser(BehandlingProsessEventDtoBuilder(eksternId).foreslåVedtak().apply { this.eventTid = eventTid.plusHours(1) }.build())

        val vaskeevent = BehandlingProsessEventDtoBuilder(eksternId).iverksettVedtak().apply {
            this.eventTid = eventTid.plusMinutes(30)
            this.eventHendelse = EventHendelse.VASKEEVENT
        }.build()

        val håndtertEvent = k9sakEventHandler.håndterVaskeevent(vaskeevent)

        assertThat(håndtertEvent).isNotNull()
        assertThat(håndtertEvent!!.eventTid).isGreaterThan(eventTid.plusHours(1))
    }
}