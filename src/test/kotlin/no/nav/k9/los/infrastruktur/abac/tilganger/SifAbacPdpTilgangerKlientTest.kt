package no.nav.k9.los.infrastruktur.abac.tilganger

import io.kotest.matchers.shouldBe
import io.ktor.client.*
import io.ktor.client.engine.mock.*
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
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import org.junit.jupiter.api.Test

internal class SifAbacPdpTilgangerKlientTest {
    private val json = """
        {
          "brukernavn": "skal-ignoreres",
          "navn": "skal-ignoreres",
          "kanVeilede": false,
          "kanBehandleKode6": true,
          "k9SaksbehandlerTilgang": { "kanSaksbehandle": true },
          "kanOppgavestyre": false,
          "kanDrifte": true
        }
    """.trimIndent()

    @Test
    fun `bruker OBO-token og K9 v2-endepunkt og mapper alle felt`() = runBlocking<Unit> {
        var antallKall = 0
        val klient = klient(MockEngine { request ->
            antallKall++
            request.url.encodedPath shouldBe "/api/k9/nav-ansatt/v2"
            request.headers[HttpHeaders.Authorization] shouldBe "Bearer obo-token"
            respond(ByteReadChannel(json), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })

        klient.hent(token()) shouldBe PdpResultat.Suksess(
            Tilganger(basis = true, kode6 = true, oppgavestyring = false, reservering = true, drift = true),
        )
        antallKall shouldBe 1
    }

    @Test
    fun `returnerer feil uten response body ved 5xx`() = runBlocking<Unit> {
        var antallKall = 0
        val klient = klient(MockEngine {
            antallKall++
            respondError(HttpStatusCode.ServiceUnavailable, "sensitiv body")
        })

        klient.hent(token()) shouldBe PdpResultat.Feil("HTTP", 503)
        antallKall shouldBe 1
    }

    @Test
    fun `retryer ikke ved permanent 4xx`() = runBlocking<Unit> {
        var antallKall = 0
        val klient = klient(MockEngine {
            antallKall++
            respondError(HttpStatusCode.Forbidden, "sensitiv body")
        })

        klient.hent(token()) shouldBe PdpResultat.Feil("HTTP", 403)
        antallKall shouldBe 1
    }

    private fun klient(engine: MockEngine) = SifAbacPdpTilgangerKlient(
        configuration = Configuration(MapApplicationConfig(TestConfiguration.asMap().map { it.key to it.value })),
        accessTokenClient = mockk<AccessTokenClient> {
            every { getOnBehalfOfAccessToken(any(), "validert-innkommende-token") } returns
                    AccessTokenResponse("obo-token", 3600, "Bearer")
        },
        scope = "api://dev-fss.k9saksbehandling.sif-abac-pdp/.default",
        httpClient = HttpClient(engine),
    )

    private fun token() = mockk<IIdToken> {
        every { value } returns "validert-innkommende-token"
    }
}
