package no.nav.k9.los.aksjonspunktbehandling

import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.repository.OppgaveRepository
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*
import kotlin.test.assertTrue


class K9TilbakeEventHandlerTest : AbstractK9LosIntegrationTest() {

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
    fun `Støtte tilbakekreving aksjonspunkt`() {
        val k9TilbakeEventHandler = get<K9TilbakeEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()

        val json = lagK9TilbakeEvent(mapOf(
            "5002" to AksjonspunktStatus.UTFØRT,
            "5001" to AksjonspunktStatus.OPPRETTET
        ))

        val event = AksjonspunktLagetTilbake().deserialize(null, json.toByteArray())!!

        k9TilbakeEventHandler.prosesser(event)
        val oppgave =
            oppgaveRepository.hent(UUID.fromString("29cbdc33-0e59-4559-96a8-c2154bf17e5a"))
        assertTrue { !oppgave.aktiv }
    }


    @Test
    fun `Støtte tilbakekreving med beslutter aksjonspunkt`() {
        val k9TilbakeEventHandler = get<K9TilbakeEventHandler>()
        val oppgaveRepository = get<OppgaveRepository>()

        val json = lagK9TilbakeEvent(mapOf(
            "7002" to AksjonspunktStatus.UTFØRT,
            "5002" to AksjonspunktStatus.UTFØRT,
            "7003" to AksjonspunktStatus.UTFØRT,
            "5003" to AksjonspunktStatus.UTFØRT,
            "5004" to AksjonspunktStatus.UTFØRT,
            "5005" to AksjonspunktStatus.OPPRETTET
        ))

        val event = AksjonspunktLagetTilbake().deserialize(null, json.toByteArray())!!

        k9TilbakeEventHandler.prosesser(event)
        val oppgave =
            oppgaveRepository.hent(UUID.fromString("29cbdc33-0e59-4559-96a8-c2154bf17e5a"))
//        assertTrue { oppgave.aktiv }
        assertTrue { oppgave.tilBeslutter }
    }

    fun lagK9TilbakeEvent(aksjonspunkter: Map<String, AksjonspunktStatus>): String {
        val aksjonspunkString = aksjonspunkter.map { (aksjonspunkt, status) ->
            "\"$aksjonspunkt\": \"${status.kode}\""
        }.joinToString(",")
        @Language("JSON")val json =
            """{
          "eksternId": "29cbdc33-0e59-4559-96a8-c2154bf17e5a",
          "fagsystem": "FPTILBAKE",
          "saksnummer": "63P3S",
          "aktørId": "1073276027910",
          "behandlingId": null,
          "eventTid": "2020-09-11T11:50:51.189546",
          "eventHendelse": "AKSJONSPUNKT_OPPRETTET",
          "behandlinStatus": null,
          "behandlingStatus": "UTRED",
          "behandlingSteg": "FAKTFEILUTSTEG",
          "behandlendeEnhet": "4863",
          "ytelseTypeKode": "FRISINN",
          "behandlingTypeKode": "BT-007",
          "opprettetBehandling": "2020-09-11T11:50:49.025",
          "aksjonspunktKoderMedStatusListe": {$aksjonspunkString},
          "href": "/fpsak/fagsak/63P3S/behandling/202/?punkt=default&fakta=default",
          "førsteFeilutbetaling": "2020-06-01",
          "feilutbetaltBeløp": 616,
          "ansvarligSaksbehandlerIdent": null
        }   """

        return json
    }
}
