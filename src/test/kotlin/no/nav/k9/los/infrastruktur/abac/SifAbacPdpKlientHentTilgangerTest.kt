package no.nav.k9.los.infrastruktur.abac

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenResponse
import no.nav.k9.los.Configuration
import no.nav.k9.los.infrastruktur.abac.tilganger.Tilganger
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class SifAbacPdpKlientHentTilgangerTest {
    @Test
    fun `begge klienter henter autoritative tilganger med OBO og riktig endepunkt`() = runBlocking {
        Områder.entries.forEach { område ->
            HttpClient(MockEngine { request ->
                request.url.encodedPath shouldBe if (område == Områder.K9) "/api/k9/nav-ansatt/v2" else "/api/ung/nav-ansatt/v2"
                request.method shouldBe HttpMethod.Get
                request.headers[HttpHeaders.Authorization] shouldBe "Bearer obo-token"
                respond(svar(område), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { http ->
                klient(område, http).hentTilganger(token()) shouldBe Tilganger(true, true, false, true, true)
            }
        }
    }

    @Test
    fun `cache skiller token uti og bruker men gjenbruker samme token`() = runBlocking {
        Områder.entries.forEach { område ->
            var kall = 0
            HttpClient(MockEngine {
                kall++
                respond(svar(område), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { http ->
                val klient = klient(område, http)
                klient.hentTilganger(token())
                klient.hentTilganger(token())
                kall shouldBe 1
                klient.hentTilganger(token(uti = "ny-token-id"))
                kall shouldBe 2
                klient.hentTilganger(token(ident = "Z654321"))
                kall shouldBe 3
            }
        }
    }

    @Test
    fun `avbryter hengende kall og cacher ikke timeout`() = runBlocking {
        Områder.entries.forEach { område ->
            var heng = true
            HttpClient(MockEngine {
                if (heng) delay(10_000)
                respond(svar(område), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { http ->
                val klient = klient(område, http, 100.milliseconds)
                shouldThrow<SifAbacPdpUtilgjengeligException> { klient.hentTilganger(token()) }
                heng = false
                klient.hentTilganger(token()).basis shouldBe true
            }
        }
    }

    @Test
    fun `HTTP feil lekker ikke responsinnhold og caches ikke`() = runBlocking {
        Områder.entries.forEach { område ->
            var feil = true
            HttpClient(MockEngine {
                if (feil) respondError(HttpStatusCode.ServiceUnavailable, "sensitivt innhold")
                else respond(svar(område), HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { http ->
                val klient = klient(område, http)
                val exception = shouldThrow<SifAbacPdpHttpException> { klient.hentTilganger(token()) }
                exception.status shouldBe 503
                exception.message shouldBe "Feil ved 'hent-tilganger' mot sif-abac-pdp: HTTP 503"
                feil = false
                klient.hentTilganger(token()).basis shouldBe true
            }
        }
    }

    private fun klient(område: Områder, http: HttpClient, timeout: Duration = 5.seconds): ISifAbacPdpKlient {
        val configuration = mockk<Configuration> { every { sifAbacPdpUrl() } returns "https://pdp.test" }
        val accessTokenClient = mockk<AccessTokenClient> {
            every { getOnBehalfOfAccessToken(any(), any()) } returns AccessTokenResponse("obo-token", 3600, "Bearer")
        }
        return when (område) {
            Områder.K9 -> SifAbacPdpKlientK9(configuration, accessTokenClient, "scope", http, timeout)
            Områder.AKTIVITETSPENGER -> SifAbacPdpKlientAktivitetspenger(configuration, accessTokenClient, "scope", http, timeout)
        }
    }

    private fun token(uti: String = "token-id", ident: String = "Z123456") = mockk<IIdToken> {
        every { value } returns "validert-token-$uti"
        every { getTokenId() } returns uti
        every { getNavIdent() } returns ident
    }

    private fun svar(område: Områder) = if (område == Områder.K9) """
        {"navn":"ignoreres", "kanVeilede":false, "kanBehandleKode6":true,
         "k9SaksbehandlerTilgang":{"kanSaksbehandle":true}, "kanOppgavestyre":false, "kanDrifte":true}
    """ else """
        {"kanVeiledeAktivitetspenger":false, "kanBehandleKode6":true,
         "aktivitetspengerDel1SaksbehandlerTilgang":{"kanSaksbehandle":false},
         "aktivitetspengerDel2SaksbehandlerTilgang":{"kanSaksbehandle":true},
         "kanOppgavestyreAktivitetspenger":false, "kanDrifte":true}
    """
}
