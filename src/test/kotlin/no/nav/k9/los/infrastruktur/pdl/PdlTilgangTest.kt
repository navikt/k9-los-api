package no.nav.k9.los.infrastruktur.pdl

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
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PdlTilgangTest {
    private val token = mockk<IIdToken> {
        every { value } returns "test-token"
        every { getNavIdent() } returns "Z123456"
    }
    private val tilgang = TestKontekstFactory.brukerkontekst(Områder.K9, token)
    private val person = """{"data":{"hentPerson":{"folkeregisteridentifikator":[],"navn":[],"kjoenn":[],"doedsfall":[]}}}"""
    private val ident = """{"data":{"hentIdenter":{"identer":[]}}}"""
    private val avslag = """{"errors":[{"extensions":{"code":"unauthorized"}}]}"""

    @Test
    fun `tidligere oppslag gir ikke person eller ident uten tilgang i valgt omraade`() = runBlocking {
        var antallKall = 0
        HttpClient(MockEngine {
            antallKall++
            respond(if (antallKall == 1) person else ident, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { http ->
            val service = service(http)
            assertNotNull(service.person("aktor", tilgang).person)
            assertNotNull(service.identifikator("fnr", tilgang).aktorId)

            Områder.entries.forEach { område ->
                val utenTilgang = TestKontekstFactory.brukerkontekst(område, token, TestKontekstFactory.INGEN_TILGANGER)
                assertEquals(PersonPdlResponse(true, null), service.person("aktor", utenTilgang))
                assertEquals(PdlResponse(true, null), service.identifikator("fnr", utenTilgang))
            }
            assertEquals(2, antallKall)
        }
    }

    @Test
    fun `nytt personoppslag autoriseres selv etter vellykket oppslag`() = runBlocking {
        var antallKall = 0
        HttpClient(MockEngine { request ->
            assertEquals("Bearer obo-token", request.headers[HttpHeaders.Authorization])
            antallKall++
            respond(if (antallKall == 1) person else avslag, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { http ->
            val service = service(http)
            assertNotNull(service.person("aktor", tilgang).person)
            assertEquals(PersonPdlResponse(true, null), service.person("aktor", tilgang))
            assertEquals(2, antallKall)
        }
    }

    @Test
    fun `nytt identoppslag autoriseres selv etter vellykket oppslag`() = runBlocking {
        var antallKall = 0
        HttpClient(MockEngine { request ->
            assertEquals("Bearer obo-token", request.headers[HttpHeaders.Authorization])
            antallKall++
            respond(if (antallKall == 1) ident else avslag, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
        }).use { http ->
            val service = service(http)
            assertNotNull(service.identifikator("fnr", tilgang).aktorId)
            assertEquals(PdlResponse(true, null), service.identifikator("fnr", tilgang))
            assertEquals(2, antallKall)
        }
    }

    private fun service(http: HttpClient): PdlService {
        val tokens = mockk<AccessTokenClient> {
            every { getOnBehalfOfAccessToken(setOf("pdl-scope"), "test-token") } returns AccessTokenResponse("obo-token", 3600, "Bearer")
        }
        return PdlService(URI("https://pdl.test"), tokens, "pdl-scope", http)
    }
}
