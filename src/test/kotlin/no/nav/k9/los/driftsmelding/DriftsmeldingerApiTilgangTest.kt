package no.nav.k9.los.driftsmelding

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.justRun
import io.mockk.mockk
import io.mockk.verify
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstFactory
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import no.nav.k9.los.områdeApi
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.time.LocalDateTime
import java.util.UUID
import kotlin.test.assertEquals

class DriftsmeldingerApiTilgangTest {
    @Test
    fun `global rute bruker AP basis men krever eksplisitt drift for skriving`() = testApplication {
        val factory = mockk<BrukerkontekstFactory>()
        coEvery { factory.utenOmråde(any()) } returns TestKontekstFactory.brukerkontekstUtenOmråde(
            tilgangerPerOmråde = mapOf(
                Områder.K9 to TestKontekstFactory.INGEN_TILGANGER,
                Områder.AKTIVITETSPENGER to TestKontekstFactory.ALLE_TILGANGER.copy(kanLeggeUtDriftsmelding = false),
            ),
        )
        val tjeneste = mockk<DriftsmeldingTjeneste>()
        every { tjeneste.hentDriftsmeldinger() } returns emptyList()
        application {
            install(ContentNegotiation) { jackson { findAndRegisterModules() } }
            install(Koin) {
                modules(module {
                    single { KoinProfile.LOCAL }
                    single { factory }
                    single { tjeneste }
                })
            }
            // Ingen områdeApi: globalruten må ikke falle tilbake til K9-kontekst.
            routing { route("driftsmeldinger") { DriftsmeldingerApis() } }
        }
        assertEquals(HttpStatusCode.OK, client.get("/driftsmeldinger").status)
        for (sti in listOf("", "/slett", "/toggle")) {
            assertEquals(HttpStatusCode.Forbidden, client.post("/driftsmeldinger$sti") {
                contentType(ContentType.Application.Json)
                setBody("ugyldig body skal ikke leses")
            }.status)
        }
        coVerify(exactly = 0) { factory.medOmråde(any(), any()) }
        verify(exactly = 0) { tjeneste.leggTilDriftsmelding(any()) }
        verify(exactly = 0) { tjeneste.slettDriftsmelding(any()) }
        verify(exactly = 0) { tjeneste.toggleDriftsmelding(any()) }
    }

    @Test
    fun `legacy bruker bare K9 rettigheter ved lesing og skriving`() = kontrollerRettigheter(legacy = true, basis = true, drift = true)

    @Test
    fun `legacy basis gir ikke driftstilgang eller fallback til AP`() = kontrollerRettigheter(legacy = true, basis = true, drift = false)

    @Test
    fun `legacy uten K9 rettigheter avvises uten fallback til AP`() = kontrollerRettigheter(legacy = true, basis = false, drift = false)

    @Test
    fun `global rute tillater eksplisitt AP drift uten K9 tilgang`() = kontrollerRettigheter(legacy = false, basis = false, drift = true)

    private fun kontrollerRettigheter(legacy: Boolean, basis: Boolean, drift: Boolean) = testApplication {
        val factory = mockk<BrukerkontekstFactory>()
        val tilganger = TestKontekstFactory.INGEN_TILGANGER.copy(
            harBasisTilgang = basis,
            kanLeggeUtDriftsmelding = drift,
        )
        if (legacy) {
            coEvery { factory.medOmråde(Områder.K9, any()) } returns
                TestKontekstFactory.brukerkontekst(Områder.K9, tilganger = tilganger)
            coEvery { factory.utenOmråde(any()) } throws IllegalStateException("AP utilgjengelig")
            coEvery { factory.medOmråde(Områder.AKTIVITETSPENGER, any()) } throws IllegalStateException("AP utilgjengelig")
        } else {
            coEvery { factory.utenOmråde(any()) } returns TestKontekstFactory.brukerkontekstUtenOmråde(
                tilgangerPerOmråde = mapOf(
                    Områder.K9 to TestKontekstFactory.INGEN_TILGANGER,
                    Områder.AKTIVITETSPENGER to tilganger,
                ),
            )
        }
        val tjeneste = mockk<DriftsmeldingTjeneste>()
        val id = UUID.randomUUID()
        every { tjeneste.hentDriftsmeldinger() } returns emptyList()
        every { tjeneste.leggTilDriftsmelding("test") } returns
            DriftsmeldingDto(id, "test", LocalDateTime.now(), false, null)
        justRun { tjeneste.slettDriftsmelding(id) }
        justRun { tjeneste.toggleDriftsmelding(DriftsmeldingSwitch(id.toString(), true)) }
        application {
            install(ContentNegotiation) { jackson { findAndRegisterModules() } }
            install(Koin) {
                modules(module {
                    single { KoinProfile.LOCAL }
                    single { factory }
                    single { tjeneste }
                })
            }
            routing {
                if (legacy) {
                    områdeApi(Områder.K9) { route("driftsmeldinger") { DriftsmeldingerApis() } }
                } else {
                    route("driftsmeldinger") { DriftsmeldingerApis() }
                }
            }
        }
        assertEquals(if (basis) HttpStatusCode.OK else HttpStatusCode.Forbidden, client.get("/driftsmeldinger").status)
        for ((sti, body) in listOf(
            "" to """{"driftsmelding":"test"}""",
            "/slett" to """{"id":"$id"}""",
            "/toggle" to """{"id":"$id","aktiv":true}""",
        )) {
            assertEquals(if (drift) HttpStatusCode.OK else HttpStatusCode.Forbidden, client.post("/driftsmeldinger$sti") {
                contentType(ContentType.Application.Json)
                setBody(if (drift) body else "ugyldig body skal ikke leses")
            }.status, sti)
        }
        coVerify(exactly = if (legacy) 4 else 0) { factory.medOmråde(Områder.K9, any()) }
        coVerify(exactly = 0) { factory.medOmråde(Områder.AKTIVITETSPENGER, any()) }
        coVerify(exactly = if (legacy) 0 else 4) { factory.utenOmråde(any()) }
        verify(exactly = if (basis) 1 else 0) { tjeneste.hentDriftsmeldinger() }
        verify(exactly = if (drift) 1 else 0) { tjeneste.leggTilDriftsmelding(any()) }
        verify(exactly = if (drift) 1 else 0) { tjeneste.slettDriftsmelding(any()) }
        verify(exactly = if (drift) 1 else 0) { tjeneste.toggleDriftsmelding(any()) }
    }
}
