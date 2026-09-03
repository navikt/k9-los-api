package no.nav.k9.los.infrastruktur.abac

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.server.config.MapApplicationConfig
import io.ktor.utils.io.ByteReadChannel
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenResponse
import no.nav.k9.los.Configuration
import no.nav.k9.los.TestConfiguration
import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IdToken
import org.junit.jupiter.api.Test

internal class SifAbacPdpKlientK9HentTilgangerTest {
    @Test
    fun `henter tilganger med OBO-token og K9 v2-endepunkt`() = runBlocking<Unit> {
        val klient = klient(MockEngine { request ->
            request.url.encodedPath shouldBe "/api/k9/nav-ansatt/v2"
            request.headers[HttpHeaders.Authorization] shouldBe "Bearer obo-token"
            respond(ByteReadChannel(SVAR), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        })

        klient.hentTilganger(token()) shouldBe Tilganger(
            basis = true,
            kode6 = true,
            oppgavestyring = false,
            reservering = true,
            drift = true,
        )
    }

    @Test
    fun `kaster typet feil uten responsinnhold`() = runBlocking<Unit> {
        val klient = klient(MockEngine { respondError(HttpStatusCode.ServiceUnavailable, "sensitivt innhold") })

        val feil = shouldThrow<SifAbacPdpHttpException> { klient.hentTilganger(token()) }

        feil.status shouldBe 503
        feil.message shouldBe "Feil ved 'hent-tilganger' mot sif-abac-pdp: HTTP 503"
    }

    private fun klient(engine: MockEngine) = SifAbacPdpKlientK9(
        configuration = Configuration(MapApplicationConfig(TestConfiguration.asMap().map { it.key to it.value })),
        accessTokenClient = mockk<AccessTokenClient> {
            every { getOnBehalfOfAccessToken(any(), "validert-innkommende-token") } returns
                AccessTokenResponse("obo-token", 3600, "Bearer")
        },
        scope = "api://dev-fss.k9saksbehandling.sif-abac-pdp/.default",
        httpClient = HttpClient(engine),
    )

    private fun token() = mockk<IdToken> {
        every { value } returns "validert-innkommende-token"
    }

    private companion object {
        val SVAR = """
            {
              "kanVeilede": false,
              "kanBehandleKode6": true,
              "k9SaksbehandlerTilgang": { "kanSaksbehandle": true },
              "kanOppgavestyre": false,
              "kanDrifte": true
            }
        """.trimIndent()
    }
}
