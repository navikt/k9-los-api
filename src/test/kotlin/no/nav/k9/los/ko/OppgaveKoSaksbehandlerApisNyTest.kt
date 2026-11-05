package no.nav.k9.los.ko

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.smiley4.ktoropenapi.OpenApi
import io.github.smiley4.ktoropenapi.openApi
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.TestApplicationBuilder
import io.ktor.server.testing.testApplication
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.områdeApi
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OppgaveKoSaksbehandlerApisNyTest {
    private val pepClient = mockk<IPepClient>()
    private val tjeneste = mockk<OppgaveKoTjeneste>()
    private val sti = "/api/fleromrade"

    @AfterEach
    fun stoppKoin() = stopKoin()

    @Test
    fun `limit og offset har standardverdier og sendes videre for begge områder`() = testApplication {
        setup()
        coEvery { pepClient.harBasisTilgang() } returns true
        coEvery { pepClient.harTilgangTilKode6() } returns false
        coEvery { tjeneste.hentOppgaverFraKøSammendrag(any(), any(), any(), any(), any(), any(), any()) } returns emptyList()

        for ((segment, område) in listOf("k9" to Områder.K9, "akt" to Områder.AKTIVITETSPENGER)) {
            for ((query, limit, offset) in listOf(
                Triple("", 10L, 0L),
                Triple("?limit=100&offset=25", 100L, 25L),
                Triple("?limit=1", 1L, 0L),
                Triple("?offset=10", 10L, 10L),
            )) {
                assertEquals(HttpStatusCode.OK, client.get("$sti/$segment/oppgaveko/1/oppgaver-i-koen$query").status)
                coVerify(exactly = 1) {
                    tjeneste.hentOppgaverFraKøSammendrag(område, false, any(), 1L, limit, true, offset)
                }
            }
        }
    }

    @Test
    fun `ugyldig limit eller offset avvises før tjenestekall`() = testApplication {
        setup()
        coEvery { pepClient.harBasisTilgang() } returns true

        for (query in listOf(
            "limit=0", "limit=-1", "limit=101", "limit=abc", "limit=", "limit=1.5",
            "limit=9223372036854775808", "offset=-1", "offset=abc", "offset=", "offset=1.5",
            "offset=9223372036854775808",
        )) {
            assertEquals(HttpStatusCode.BadRequest, client.get("$sti/k9/oppgaveko/1/oppgaver-i-koen?$query").status, query)
        }
        verify { tjeneste wasNot Called }
    }

    @Test
    fun `uten basistilgang avvises forespørselen før validering og tjenestekall`() = testApplication {
        setup()
        coEvery { pepClient.harBasisTilgang() } returns false

        for (query in listOf("", "?limit=101&offset=-1")) {
            assertEquals(HttpStatusCode.Forbidden, client.get("$sti/k9/oppgaveko/1/oppgaver-i-koen$query").status)
        }
        verify { tjeneste wasNot Called }
    }

    @Test
    fun `OpenAPI beskriver valgfrie parametre og ugyldige verdier`() = testApplication {
        setup()
        val spec = ObjectMapper().readTree(client.get("/openapi.json").bodyAsText())
        val get = spec["paths"]["$sti/{omrade}/oppgaveko/{id}/oppgaver-i-koen"]["get"]
        for (navn in listOf("limit", "offset")) {
            val parameter = get["parameters"].single { it["name"].textValue() == navn }
            assertEquals("query", parameter["in"].textValue())
            assertFalse(parameter["required"].asBoolean())
        }
        assertTrue(get["responses"].has("400"))
    }

    private fun TestApplicationBuilder.setup() {
        application {
            install(Koin) {
                modules(module {
                    single { pepClient }
                    single { tjeneste }
                    single { mockk<SaksbehandlerRepository>() }
                    single { RequestContextService(KoinProfile.LOCAL) }
                })
            }
            install(ContentNegotiation) { jackson { findAndRegisterModules() } }
            install(OpenApi)
            routing {
                route(sti) {
                    områdeApi {
                        route("oppgaveko") { OppgaveKoSaksbehandlerApisNy() }
                    }
                }
                route("openapi.json") { openApi() }
            }
        }
    }
}
