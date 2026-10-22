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
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
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
import no.nav.k9.los.infrastruktur.utils.IkkeImplementertException
import no.nav.k9.los.nøkkeltall.avdelingsleder.inngangogferdigstilte.InngangOgFerdigstilteResponse
import no.nav.k9.los.nøkkeltall.avdelingsleder.inngangogferdigstilte.InngangOgFerdigstilteService
import no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet.FerdigstiltePerEnhetGruppe
import no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet.FerdigstiltePerEnhetResponse
import no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet.K9FerdigstiltePerEnhetService
import no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.StatusFordelingResponse
import no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.StatusFordelingService
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.coroutines.coroutineContext
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AvdelingslederNøkkeltallApisTest {
    private val pepClient = mockk<IPepClient>()
    private val statusFordeling = mockk<StatusFordelingService>()
    private val inngangOgFerdigstilte = mockk<InngangOgFerdigstilteService>()
    private val perEnhet = mockk<K9FerdigstiltePerEnhetService>()
    private val base = "/api/wip"
    private val nøkkeltall = "avdelingsleder/nokkeltall"
    private val endepunkter = listOf("statusfordeling", "inngang-og-ferdigstilte", "ferdigstilte-per-enhet")

    @AfterEach
    fun stoppKoin() = stopKoin()

    @Test
    fun `statusfordeling bruker område og kode6-tilgang fra request-konteksten`() = testApplication {
        setup()
        val tilgangsområder = mutableListOf<Områder>()
        coEvery { pepClient.erOppgaveStyrer() } coAnswers {
            tilgangsområder += coroutineContext.område()
            true
        }
        coEvery { pepClient.harTilgangTilKode6() } coAnswers {
            coroutineContext.område() == Områder.AKTIVITETSPENGER
        }
        every { statusFordeling.hentVerdi(any(), any()) } returns StatusFordelingResponse()

        for (område in listOf("k9", "akt")) {
            val response = client.get("$base/$område/$nøkkeltall/statusfordeling")
            assertEquals(HttpStatusCode.OK, response.status)
            assertTrue(ObjectMapper().readTree(response.bodyAsText()).has("tall"))
        }

        assertEquals(listOf(Områder.K9, Områder.AKTIVITETSPENGER), tilgangsområder)
        verify(exactly = 1) { statusFordeling.hentVerdi(Områder.K9, false) }
        verify(exactly = 1) { statusFordeling.hentVerdi(Områder.AKTIVITETSPENGER, true) }
    }

    @Test
    fun `alle nøkkeltall krever oppgavestyringstilgang også før 501`() = testApplication {
        setup()
        coEvery { pepClient.erOppgaveStyrer() } returns false

        for (område in listOf("k9", "akt")) {
            for (endepunkt in endepunkter) {
                assertEquals(HttpStatusCode.Forbidden, client.get("$base/$område/$nøkkeltall/$endepunkt").status)
            }
        }

        coVerify(exactly = 0) { pepClient.harTilgangTilKode6() }
        verify { listOf(statusFordeling, inngangOgFerdigstilte, perEnhet) wasNot Called }
    }

    @Test
    fun `aktivitetspenger får 501 for øvrige nøkkeltall uten å kalle K9-tjenester`() = testApplication {
        setup()
        coEvery { pepClient.erOppgaveStyrer() } returns true

        assertEquals(HttpStatusCode.NotImplemented, client.get("$base/akt/$nøkkeltall/ferdigstilte-per-enhet").status)

        verify { listOf(inngangOgFerdigstilte, perEnhet) wasNot Called }
    }

    @Test
    fun `K9 får eksisterende nøkkeltall og queryparametre på ny route`() = testApplication {
        setup()
        coEvery { pepClient.erOppgaveStyrer() } returns true
        every { inngangOgFerdigstilte.hentCachetVerdi(Områder.K9) } returns InngangOgFerdigstilteResponse(null, emptyList(), emptyList(), emptyList())
        every { perEnhet.hentCachetVerdi(any(), any()) } returns FerdigstiltePerEnhetResponse()

        assertEquals(HttpStatusCode.OK, client.get("$base/k9/$nøkkeltall/inngang-og-ferdigstilte").status)
        assertEquals(HttpStatusCode.OK, client.get("$base/k9/$nøkkeltall/ferdigstilte-per-enhet").status)
        assertEquals(HttpStatusCode.OK, client.get("$base/k9/$nøkkeltall/ferdigstilte-per-enhet?gruppe=PLEIEPENGER_SYKT_BARN&uker=4").status)
        verify(exactly = 1) { perEnhet.hentCachetVerdi(FerdigstiltePerEnhetGruppe.ALLE, 2) }
        verify(exactly = 1) { perEnhet.hentCachetVerdi(FerdigstiltePerEnhetGruppe.PLEIEPENGER_SYKT_BARN, 4) }
    }

    @Test
    fun `inngang og ferdigstilte bruker områdets tjeneste for både K9 og aktivitetspenger`() = testApplication {
        setup()
        val tilgangsområder = mutableListOf<Områder>()
        coEvery { pepClient.erOppgaveStyrer() } coAnswers {
            tilgangsområder += coroutineContext.område()
            true
        }
        every { inngangOgFerdigstilte.hentCachetVerdi(any()) } returns
            InngangOgFerdigstilteResponse(null, emptyList(), emptyList(), emptyList())

        for (område in listOf("k9", "akt")) {
            assertEquals(HttpStatusCode.OK, client.get("$base/$område/$nøkkeltall/inngang-og-ferdigstilte").status)
            assertEquals(HttpStatusCode.NotFound, client.get("$base/$område/$nøkkeltall/dagens-tall").status)
            assertEquals(HttpStatusCode.NotFound, client.get("$base/$område/$nøkkeltall/status").status)
        }

        assertEquals(listOf(Områder.K9, Områder.AKTIVITETSPENGER), tilgangsområder)
        verify(exactly = 1) { inngangOgFerdigstilte.hentCachetVerdi(Områder.K9) }
        verify(exactly = 1) { inngangOgFerdigstilte.hentCachetVerdi(Områder.AKTIVITETSPENGER) }
    }

    @Test
    fun `ugyldige parametre gir 400`() = testApplication {
        setup()
        coEvery { pepClient.erOppgaveStyrer() } returns true

        for (parametre in listOf("uker=3", "uker=tekst", "gruppe=UKJENT")) {
            assertEquals(HttpStatusCode.BadRequest, client.get("$base/k9/$nøkkeltall/ferdigstilte-per-enhet?$parametre").status)
        }
        verify { perEnhet wasNot Called }
    }

    @Test
    fun `alle nøkkeltall er dokumentert med område og støttede responser`() = testApplication {
        setup()

        val spec = ObjectMapper().readTree(client.get("/openapi.json").bodyAsText())
        for (endepunkt in endepunkter) {
            val operation = spec["paths"]["$base/{omrade}/$nøkkeltall/$endepunkt"]["get"]
            assertTrue(operation["responses"].has("200"))
            assertTrue(operation["responses"].has("403"))
            if (endepunkt == "ferdigstilte-per-enhet") assertTrue(operation["responses"].has("501"))
        }
        assertEquals("hentInngangOgFerdigstilte", spec["paths"]["$base/{omrade}/$nøkkeltall/inngang-og-ferdigstilte"]["get"]["operationId"].textValue())
        assertTrue(!spec["paths"].has("$base/{omrade}/$nøkkeltall/dagens-tall"))
    }

    private fun TestApplicationBuilder.setup() {
        application {
            install(Koin) {
                modules(module {
                    single { pepClient }
                    single { statusFordeling }
                    single { inngangOgFerdigstilte }
                    single { perEnhet }
                    single { RequestContextService(KoinProfile.LOCAL) }
                })
            }
            install(ContentNegotiation) { jackson { findAndRegisterModules() } }
            install(StatusPages) {
                exception<IkkeImplementertException> { call, e -> call.respond(HttpStatusCode.NotImplemented, e.message) }
            }
            install(OpenApi)
            routing {
                route(base) {
                    områdeApi {
                        route(nøkkeltall) { AvdelingslederNøkkeltallApis() }
                    }
                }
                route("openapi.json") { openApi() }
            }
        }
    }
}
