package no.nav.k9.los.infrastruktur.azuregraph

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenClient
import no.nav.helse.dusseldorf.oauth2.client.AccessTokenResponse
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID
import kotlin.test.assertEquals

class AzureGraphServiceTest {
    private val token = mockk<IIdToken> {
        every { value } returns "test-token"
        every { getNavIdent() } returns "Z123456"
    }
    private val tokens = mockk<AccessTokenClient> {
        every { getOnBehalfOfAccessToken(any(), "test-token") } returns AccessTokenResponse("obo-token", 3600, "Bearer")
        every { getClientCredentialsAccessToken(any()) } returns AccessTokenResponse("app-token", 3600, "Bearer")
    }

    @Test
    fun `enhet hentes med eksplisitt token og feil token avvises`() = runBlocking {
        var antallKall = 0
        HttpClient(MockEngine { request ->
            antallKall++
            assertEquals("/v1.0/me", request.url.encodedPath)
            assertEquals("officeLocation", request.url.parameters["\$select"])
            assertEquals("Bearer obo-token", request.headers[HttpHeaders.Authorization])
            respond("""{"@odata.context":"test","officeLocation":"3450"}""", HttpStatusCode.OK)
        }).use { http ->
            val service = AzureGraphService(tokens, http)
            assertEquals("3450", service.hentEnhet("Z123456", token))
            assertThrows<IllegalArgumentException> { service.hentEnhet("Z654321", token) }
            assertEquals(1, antallKall)
        }
    }

    @Test
    fun `manglende enhet feiler fremfor aa skrive tom enhet`() = runBlocking {
        HttpClient(MockEngine {
            respond("""{"@odata.context":"test","officeLocation":null}""", HttpStatusCode.OK)
        }).use { http ->
            assertThrows<IllegalStateException> { AzureGraphService(tokens, http).hentEnhet("Z123456", token) }
        }
    }

    @Test
    fun `grupper fra eksplisitt kontekst caches paa saksbehandler`() = runBlocking {
        val gruppe = UUID.randomUUID()
        var antallKall = 0
        HttpClient(MockEngine { request ->
            antallKall++
            assertEquals("/v1.0/me/memberOf", request.url.encodedPath)
            assertEquals("Bearer obo-token", request.headers[HttpHeaders.Authorization])
            respond("""{"value":[{"id":"$gruppe"}]}""", HttpStatusCode.OK)
        }).use { http ->
            val service = AzureGraphService(tokens, http)
            Områder.entries.forEach { område ->
                assertEquals(setOf(gruppe), service.hentGrupper(TestKontekstFactory.brukerkontekst(område, token)))
            }
            assertEquals(1, antallKall)
        }
    }

    @Test
    fun `identoppslag bruker app-token og cacher baade brukerid og grupper`() = runBlocking {
        val userId = UUID.randomUUID()
        val gruppe = UUID.randomUUID()
        var antallKall = 0
        HttpClient(MockEngine { request ->
            antallKall++
            assertEquals("Bearer app-token", request.headers[HttpHeaders.Authorization])
            val svar = when (request.url.encodedPath) {
                "/v1.0/users" -> """{"value":[{"id":"$userId"}]}"""
                "/v1.0/users/$userId/memberOf" -> """{"value":[{"id":"$gruppe"}]}"""
                else -> error("Uventet Graph-endepunkt")
            }
            respond(svar, HttpStatusCode.OK)
        }).use { http ->
            val service = AzureGraphService(tokens, http)
            repeat(2) { assertEquals(setOf(gruppe), service.hentGrupper("Z123456")) }
            assertEquals(2, antallKall)
        }
    }
}
