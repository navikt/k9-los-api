package no.nav.k9.los.nøkkeltall

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
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.infrastruktur.rest.områdeApi
import no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte.NyeOgFerdigstilteResponse
import no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte.NyeOgFerdigstilteService
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.coroutines.coroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SaksbehandlerNøkkeltallApiTest {
    private val pepClient = mockk<IPepClient>()
    private val service = mockk<NyeOgFerdigstilteService>()
    private val sti = "/api/wip"

    @AfterEach
    fun stoppKoin() = stopKoin()

    @Test
    fun `basistilgang gir nøkkeltall fra riktig område med ALLE som standard`() = testApplication {
        setup()
        val områder = mutableListOf<Områder>()
        coEvery { pepClient.harBasisTilgang() } coAnswers {
            områder += coroutineContext.område()
            true
        }
        every { service.grupper(any()) } returns listOf(KodeOgNavn("ALLE", "Alle"))
        every { service.hentCachetVerdi(any(), "ALLE") } returns NyeOgFerdigstilteResponse(null)

        for (område in listOf("k9", "akt")) {
            val response = client.get("$sti/$område/saksbehandler/nokkeltall")
            assertEquals(HttpStatusCode.OK, response.status)
            val body = ObjectMapper().readTree(response.bodyAsText())
            assertTrue(body.has("grupper") && body.has("kolonner") && body.has("serier"))
        }
        assertEquals(listOf(Områder.K9, Områder.AKTIVITETSPENGER), områder)
        verify(exactly = 1) { service.hentCachetVerdi(Områder.K9, "ALLE") }
        verify(exactly = 1) { service.hentCachetVerdi(Områder.AKTIVITETSPENGER, "ALLE") }
        coVerify(exactly = 0) { pepClient.erOppgaveStyrer() }
    }

    @Test
    fun `gruppe valideres mot området og sendes videre`() = testApplication {
        setup()
        coEvery { pepClient.harBasisTilgang() } returns true
        every { service.grupper(Områder.K9) } returns listOf(KodeOgNavn("PUNSJ", "Punsj"))
        every { service.grupper(Områder.AKTIVITETSPENGER) } returns listOf(
            KodeOgNavn("DEL1", "Del 1"), KodeOgNavn("KLAGE", "Klager"), KodeOgNavn("FEILUTBETALING", "Feilutbetalinger"),
        )
        every { service.hentCachetVerdi(any(), any()) } returns NyeOgFerdigstilteResponse(null)

        assertEquals(HttpStatusCode.OK, client.get("$sti/k9/saksbehandler/nokkeltall?gruppe=PUNSJ").status)
        for (gruppe in listOf("DEL1", "KLAGE", "FEILUTBETALING")) {
            assertEquals(HttpStatusCode.OK, client.get("$sti/akt/saksbehandler/nokkeltall?gruppe=$gruppe").status)
            verify(exactly = 1) { service.hentCachetVerdi(Områder.AKTIVITETSPENGER, gruppe) }
        }
        assertEquals(HttpStatusCode.BadRequest, client.get("$sti/k9/saksbehandler/nokkeltall?gruppe=DEL1").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("$sti/akt/saksbehandler/nokkeltall?gruppe=PUNSJ").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("$sti/akt/saksbehandler/nokkeltall?gruppe=").status)
        verify(exactly = 4) { service.hentCachetVerdi(any(), any()) }
    }

    @Test
    fun `uten basistilgang blir både gyldige og ugyldige grupper avvist før servicekall`() = testApplication {
        setup()
        coEvery { pepClient.harBasisTilgang() } returns false

        for (område in listOf("k9", "akt")) {
            assertEquals(HttpStatusCode.Forbidden, client.get("$sti/$område/saksbehandler/nokkeltall").status)
            assertEquals(HttpStatusCode.Forbidden, client.get("$sti/$område/saksbehandler/nokkeltall?gruppe=UKJENT").status)
        }
        verify { service wasNot Called }
    }

    @Test
    fun `OpenAPI beskriver gruppeparameter og responskoder`() = testApplication {
        setup()
        val spec = ObjectMapper().readTree(client.get("/openapi.json").bodyAsText())
        val get = spec["paths"]["$sti/{omrade}/saksbehandler/nokkeltall"]["get"]
        assertEquals("hentSaksbehandlerNøkkeltall", get["operationId"].textValue())
        val parameter = get["parameters"].single { it["name"].textValue() == "gruppe" }
        assertEquals("query", parameter["in"].textValue())
        assertFalse(parameter["required"].asBoolean())
        for (status in listOf("200", "400", "403")) assertTrue(get["responses"].has(status))
    }

    private fun TestApplicationBuilder.setup() {
        application {
            install(Koin) {
                modules(module {
                    single { pepClient }
                    single { service }
                    single { RequestContextService(KoinProfile.LOCAL) }
                })
            }
            install(ContentNegotiation) { jackson { findAndRegisterModules() } }
            install(OpenApi)
            routing {
                route(sti) {
                    områdeApi {
                        route("saksbehandler/nokkeltall") { SaksbehandlerNøkkeltallApi() }
                    }
                }
                route("openapi.json") { openApi() }
            }
        }
    }
}
