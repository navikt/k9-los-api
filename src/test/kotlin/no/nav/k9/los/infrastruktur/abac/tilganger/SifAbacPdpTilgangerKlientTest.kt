package no.nav.k9.los.infrastruktur.abac.tilganger

import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.utils.io.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenResponse
import no.nav.k9.los.Configuration
import no.nav.k9.los.TestConfiguration
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SifAbacPdpTilgangerKlientTest {

    private val k9DtoJson = """
        {
          "brukernavn": "Z123456",
          "navn": "Saksbehandler Sara",
          "kanVeilede": false,
          "kanBehandleKode6": true,
          "kanBehandleKode7": false,
          "kanBehandleKodeEgenAnsatt": false,
          "kanLeseHistoriskSak": false,
          "funksjonellTid": "2026-08-27T10:00:00",
          "skalViseDetaljerteFeilmeldinger": true,
          "k9SaksbehandlerTilgang": {
            "kanSaksbehandle": true,
            "kanBeslutte": false,
            "kanOverstyre": false
          },
          "kanOppgavestyre": false,
          "kanDrifte": false
        }
    """.trimIndent()

    private val ungDtoJson = """
        {
          "brukernavn": "Z123456",
          "navn": "Saksbehandler Sara",
          "kanVeiledeUngdomsprogramytelse": false,
          "kanVeiledeAktivitetspenger": true,
          "kanDrifte": true,
          "erUngdomsprogramveileder": false,
          "kanBehandleKode6": false,
          "kanBehandleKode7": false,
          "kanBehandleKodeEgenAnsatt": false,
          "funksjonellTid": "2026-08-27T10:00:00",
          "skalViseDetaljerteFeilmeldinger": true,
          "ungdomsprogramytelseSaksbehandlerTilgang": {
            "kanSaksbehandle": false,
            "kanBeslutte": false,
            "kanOverstyre": false
          },
          "aktivitetspengerDel1SaksbehandlerTilgang": {
            "kanSaksbehandle": false,
            "kanBeslutte": false,
            "kanOverstyre": false
          },
          "aktivitetspengerDel2SaksbehandlerTilgang": {
            "kanSaksbehandle": true,
            "kanBeslutte": false,
            "kanOverstyre": false
          },
          "aktuelleYtelser": ["AKTIVITETSPENGER"],
          "kanOppgavestyreAktivitetspenger": true
        }
    """.trimIndent()

    private fun klient(handler: MockRequestHandler) = SifAbacPdpTilgangerKlient(
        configuration = Configuration(MapApplicationConfig(
            TestConfiguration.asMap().map { (k, v) -> k to v }.toList()
        )),
        accessTokenClient = fakeTokenClient(),
        scope = "api://dev-fss.k9saksbehandling.sif-abac-pdp/.default",
        httpClient = HttpClient(MockEngine(handler)),
    )

    private fun fakeTokenClient(): AccessTokenClient {
        val response = AccessTokenResponse("obo-token", 3600, "Bearer")
        val client = mockk<AccessTokenClient>()
        every { client.getOnBehalfOfAccessToken(any(), any()) } returns response
        return client
    }

    private fun idToken(navIdent: String = "Z123456") = mockk<IdToken> {
        every { value } returns "bruker-token"
        every { getNavIdent() } returns navIdent
    }

    private fun jsonHandler(body: String): MockRequestHandler = {
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json")
        )
    }

    @Test
    fun `mapper K9-DTO til OmrådeTilganger`() {
        val tilganger = runBlocking {
            klient(jsonHandler(k9DtoJson)).tilganger(Områder.K9, idToken())
        }

        tilganger.harBasisTilgang shouldBe true // kanSaksbehandle
        tilganger.harTilgangTilKode6 shouldBe true
        tilganger.erOppgavestyrer shouldBe false
        tilganger.harTilgangTilReserveringAvOppgaver shouldBe true // kanSaksbehandle, ikke veileder
        tilganger.kanLeggeUtDriftsmelding shouldBe false
    }

    @Test
    fun `mapper ung-DTO til OmrådeTilganger — kun aktivitetspenger teller for basis, veileder gir ikke reservering`() {
        val tilganger = runBlocking {
            klient(jsonHandler(ungDtoJson)).tilganger(Områder.AKTIVITETSPENGER, idToken())
        }

        tilganger.harBasisTilgang shouldBe true
        tilganger.harTilgangTilKode6 shouldBe false
        tilganger.erOppgavestyrer shouldBe true
        tilganger.harTilgangTilReserveringAvOppgaver shouldBe true // kanSaksbehandle del2
        tilganger.kanLeggeUtDriftsmelding shouldBe true
    }

    @Test
    fun `K9-DTO uten kanDrifte-felt tolkes som false (PDP har ikke levert feltet ennå)`() {
        val utenDrift = k9DtoJson.replace("""  "kanDrifte": false"""", "")
        val tilganger = runBlocking {
            klient(jsonHandler(utenDrift)).tilganger(Områder.K9, idToken())
        }
        tilganger.kanLeggeUtDriftsmelding shouldBe false
    }

    @Test
    fun `K9 veileder uten saksbehandlertilgang får basis, men ikke reservering`() {
        val veilederJson = k9DtoJson
            .replace("\"kanVeilede\": false", "\"kanVeilede\": true")
            .replace("\"kanSaksbehandle\": true", "\"kanSaksbehandle\": false")
        val tilganger = runBlocking {
            klient(jsonHandler(veilederJson)).tilganger(Områder.K9, idToken())
        }
        tilganger.harBasisTilgang shouldBe true
        tilganger.harTilgangTilReserveringAvOppgaver shouldBe false
    }

    @Test
    fun `cacher per navIdent og område - kun ett HTTP-kall ved gjentatte oppslag`() {
        var antallKall = 0
        val klient = klient { request ->
            antallKall++
            request.url.encodedPath shouldBe "/api/k9/nav-ansatt/v2"
            request.headers[HttpHeaders.Authorization] shouldBe "Bearer obo-token"
            respond(
                content = ByteReadChannel(k9DtoJson),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        runBlocking {
            klient.tilganger(Områder.K9, idToken())
            klient.tilganger(Områder.K9, idToken())
        }
        antallKall shouldBe 1
    }

    @Test
    fun `cache er atskilt per område og per ident`() {
        val paths = mutableListOf<String>()
        val klient = klient { request ->
            paths.add(request.url.encodedPath)
            val body = if (request.url.encodedPath.contains("/k9/")) k9DtoJson else ungDtoJson
            respond(
                content = ByteReadChannel(body),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json")
            )
        }

        runBlocking {
            klient.tilganger(Områder.K9, idToken())
            klient.tilganger(Områder.AKTIVITETSPENGER, idToken())
            klient.tilganger(Områder.K9, idToken("Z654321"))
        }
        paths shouldBe listOf("/api/k9/nav-ansatt/v2", "/api/ung/nav-ansatt/v2", "/api/k9/nav-ansatt/v2")
    }

    @Test
    fun `feiler fail-closed ved HTTP-feil, og cacher ikke feilen`() {
        var antallKall = 0
        val klient = klient {
            antallKall++
            if (antallKall < 4) { // Retry gjør 3 forsøk per kall
                respondError(HttpStatusCode.InternalServerError)
            } else {
                respond(
                    content = ByteReadChannel(k9DtoJson),
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json")
                )
            }
        }

        runBlocking {
            assertThrows<IllegalStateException> {
                klient.tilganger(Områder.K9, idToken())
            }
            // Feilen er ikke cachet — nytt forsøk lykkes
            klient.tilganger(Områder.K9, idToken()).harBasisTilgang shouldBe true
        }
    }
}
