package no.nav.k9.los.infrastruktur.abac

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.TextContent
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenResponse
import no.nav.k9.los.Configuration
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.sif.abac.kontrakt.abac.Diskresjonskode
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import org.junit.jupiter.api.Test

class SifAbacPdpKlientAktivitetspengerTest {
    @Test
    fun `sak bruker aktivitetspenger domenet OBO og sporingshint kontrakten`() = runBlocking {
        HttpClient(MockEngine { request ->
            request.method shouldBe HttpMethod.Post
            request.url.encodedPath shouldBe "/api/tilgangskontroll/v2/aktivitetspenger/sak-sporingshint"
            request.headers[HttpHeaders.Authorization] shouldBe "Bearer obo-token"
            val body = LosObjectMapper.instance.readTree((request.body as TextContent).text)
            body["operasjon"]["resource"].asText() shouldBe "FAGSAK"
            body["operasjon"]["action"].asText() shouldBe "READ"
            respond("""{"tilgangsbeslutning":{"harTilgang":true,"årsakerForIkkeTilgang":[]},"aktørIdForSporingslogg":[]}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { http ->
            klient(http).harTilgangTilSak(Action.read, SaksnummerDto("123456"), IdTokenLocal()) shouldBe true
        }
    }

    @Test
    fun `personer bruker aktivitetspenger domenet og serverens beslutning`() = runBlocking {
        listOf(false, true).forEach { tillatt ->
            HttpClient(MockEngine { request ->
                request.method shouldBe HttpMethod.Post
                request.url.encodedPath shouldBe "/api/tilgangskontroll/v2/aktivitetspenger/personer"
                request.headers[HttpHeaders.Authorization] shouldBe "Bearer obo-token"
                respond("""{"harTilgang":$tillatt,"årsakerForIkkeTilgang":[]}""",
                    HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { http ->
                klient(http).harTilgangTilPersoner(Action.read, listOf(AktørId("1234567890123")), IdTokenLocal()) shouldBe tillatt
            }
        }
    }

    @Test
    fun `reserver og update beholder serveravslag uten alternativ policy`() = runBlocking {
        listOf(Action.reserver, Action.update).forEach { action ->
            var kall = 0
            HttpClient(MockEngine { request ->
                kall++
                val body = LosObjectMapper.instance.readTree((request.body as TextContent).text)
                body["operasjon"]["action"].asText() shouldBe action.name.uppercase()
                val beslutning = """{"harTilgang":false,"årsakerForIkkeTilgang":["TEKNISK_FEIL"]}"""
                val svar = when (request.url.encodedPath) {
                    "/api/tilgangskontroll/v2/aktivitetspenger/sak-sporingshint" ->
                        """{"tilgangsbeslutning":$beslutning,"aktørIdForSporingslogg":[]}"""
                    "/api/tilgangskontroll/v2/aktivitetspenger/personer" -> beslutning
                    else -> error("Ukjent endepunkt")
                }
                respond(svar, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
            }).use { http ->
                val klient = klient(http)
                klient.harTilgangTilSak(action, SaksnummerDto("123456"), IdTokenLocal()) shouldBe false
                klient.harTilgangTilPersoner(action, listOf(AktørId("1234567890123")), IdTokenLocal()) shouldBe false
                kall shouldBe 2
            }
        }
    }

    @Test
    fun `diskresjonskoder bruker felles PIP med systemtoken og returnerer reelle koder`() = runBlocking {
        val stier = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            stier.add(request.url.encodedPath)
            request.method shouldBe HttpMethod.Post
            request.headers[HttpHeaders.Authorization] shouldBe "Bearer system-token"
            respond("""["KODE6","SKJERMET"]""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { http ->
            val klient = klient(http)
            klient.diskresjonskoderSak(SaksnummerDto("123456")) shouldBe setOf(Diskresjonskode.KODE6, Diskresjonskode.SKJERMET)
            klient.diskresjonskoderPerson(AktørId("1234567890123")) shouldBe setOf(Diskresjonskode.KODE6, Diskresjonskode.SKJERMET)
            stier shouldBe listOf("/api/diskresjonskoder/ung/sak", "/api/diskresjonskoder/person")
        }
    }

    @Test
    fun `annen bruker avvises eksplisitt uten oppdiktede HTTP endepunkter`() = runBlocking {
        HttpClient(MockEngine { error("Ingen serverkontrakt finnes for annen bruker") }).use { http ->
            val klient = klient(http)
            Action.entries.forEach { action ->
                shouldThrow<UnsupportedOperationException> {
                    klient.harTilgangTilSak(action, SaksnummerDto("123456"), "Z123456", emptySet())
                }
                shouldThrow<UnsupportedOperationException> {
                    klient.harTilgangTilPersoner(action, listOf(AktørId("1234567890123")), "Z123456", emptySet())
                }
            }
        }
    }

    private fun klient(http: HttpClient) = SifAbacPdpKlientAktivitetspenger(
        configuration = mockk<Configuration> { every { sifAbacPdpUrl() } returns "https://pdp.test" },
        accessTokenClient = mockk<AccessTokenClient> {
            every { getOnBehalfOfAccessToken(any(), any()) } returns AccessTokenResponse("obo-token", 3600, "Bearer")
            every { getClientCredentialsAccessToken(any()) } returns AccessTokenResponse("system-token", 3600, "Bearer")
        },
        scope = "scope",
        httpClient = http,
    )
}
