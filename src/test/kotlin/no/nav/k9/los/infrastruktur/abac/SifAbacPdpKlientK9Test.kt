package no.nav.k9.los.infrastruktur.abac

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
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.sif.abac.kontrakt.abac.dto.SaksnummerDto
import no.nav.sif.abac.kontrakt.person.AktørId
import org.junit.jupiter.api.Test
import java.util.UUID

class SifAbacPdpKlientK9Test {
    @Test
    fun `annen saksbehandlers grupper sendes i begge systemtoken kontrakter`() = runBlocking {
        val gruppe = UUID.randomUUID()
        val stier = mutableListOf<String>()
        HttpClient(MockEngine { request ->
            stier.add(request.url.encodedPath)
            request.method shouldBe HttpMethod.Post
            request.headers[HttpHeaders.Authorization] shouldBe "Bearer system-token"
            val body = LosObjectMapper.instance.readTree((request.body as TextContent).text)
            body["operasjon"]["action"].asText() shouldBe "RESERVER"
            body["ident"].asText() shouldBe "Z123456"
            body["rettighetsgrupper"].map { it.asText() } shouldBe listOf(gruppe.toString())
            respond("""{"harTilgang":false,"årsakerForIkkeTilgang":[]}""",
                HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { http ->
            val klient = SifAbacPdpKlientK9(
                configuration = mockk<Configuration> { every { sifAbacPdpUrl() } returns "https://pdp.test" },
                accessTokenClient = mockk<AccessTokenClient> {
                    every { getClientCredentialsAccessToken(any()) } returns AccessTokenResponse("system-token", 3600, "Bearer")
                },
                scope = "scope", httpClient = http,
            )
            klient.harTilgangTilSak(Action.reserver, SaksnummerDto("123456"), "Z123456", setOf(gruppe)) shouldBe false
            klient.harTilgangTilPersoner(Action.reserver, listOf(AktørId("1234567890123")), "Z123456", setOf(gruppe)) shouldBe false
            stier shouldBe listOf("/api/tilgangskontroll/v2/k9/sak-grupper", "/api/tilgangskontroll/v2/k9/personer-grupper")
        }
    }
}
