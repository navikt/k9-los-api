package no.nav.k9.los.reservasjon

import io.github.smiley4.ktoropenapi.OpenApi
import io.ktor.client.request.post
import io.ktor.client.request.get
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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
import no.nav.k9.los.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.områdeApi
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelUtenOmrådeDto
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals
import tools.jackson.module.kotlin.jacksonObjectMapper

class ReservasjonApisNyTest {
    private val pepClient = mockk<IPepClient>()
    private val tjeneste = mockk<ReservasjonApisTjeneste>()
    private val repository = mockk<SaksbehandlerRepository>()
    private val innlogget = mockk<Saksbehandler>()
    private val sti = "/api/fleromrade"
    private val jsonBody = """{"oppgaveNøkkel":{"oppgaveEksternId":"oppgave","oppgaveTypeEksternId":"type"},"navident":"Z999999"}"""

    @AfterEach
    fun stoppKoin() = stopKoin()

    @Test
    fun `saksbehandlerlisten inkluderer enhet og filtrerer bort manglende navn og ident`() = testApplication {
        setup()
        coEvery { pepClient.harBasisTilgang() } returns true
        coEvery { pepClient.harTilgangTilKode6() } returns false
        for ((segment, område) in listOf("k9" to Områder.K9, "akt" to Områder.AKTIVITETSPENGER)) {
            every { repository.hentAlleSaksbehandlere(område, false) } returns listOf(
                Saksbehandler(1, "Z111111", "Kari", "kari@nav.no", "NAV 2103", listOf(område), false),
                Saksbehandler(2, "Z222222", "Lars", "lars@nav.no", null, listOf(område), false),
                Saksbehandler(3, " ", "Uten ident", "uten-ident@nav.no", "NAV 2103", listOf(område), false),
                Saksbehandler(4, "Z444444", null, "uten-navn@nav.no", "NAV 2103", listOf(område), false),
            )
            val response = client.get("$sti/$segment/reservasjoner/saksbehandlere")
            assertEquals(HttpStatusCode.OK, response.status)
            val json = jacksonObjectMapper().readTree(response.bodyAsText())
            assertEquals(2, json.size())
            assertEquals("NAV 2103", json[0]["enhet"].asString())
            assertEquals("Z222222", json[1]["brukerIdent"].asString())
            assertEquals(true, json[1]["enhet"].isNull)
            verify(exactly = 1) { repository.hentAlleSaksbehandlere(område, false) }
        }
    }

    @Test
    fun `saksbehandlerlisten krever basistilgang`() = testApplication {
        setup()
        coEvery { pepClient.harBasisTilgang() } returns false
        assertEquals(HttpStatusCode.Forbidden, client.get("$sti/k9/reservasjoner/saksbehandlere").status)
        verify { repository wasNot Called }
    }

    @Test
    fun `reserverer for mottaker i begge områder og returnerer tom 200`() = testApplication {
        setup()
        coEvery { pepClient.harTilgangTilReserveringAvOppgaver() } returns true
        coEvery { pepClient.harTilgangTilKode6() } returns true
        every { repository.finnSaksbehandlerMedIdent("Z123456", true) } returns innlogget
        coEvery { tjeneste.reserverOppgaveForSaksbehandler(any(), any(), innlogget) } returns Unit

        for ((segment, område) in listOf("k9" to Områder.K9, "akt" to Områder.AKTIVITETSPENGER)) {
            val response = client.post("$sti/$segment/reservasjoner/reserver-for-saksbehandler") {
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("", response.bodyAsText())
            coVerify(exactly = 1) {
                tjeneste.reserverOppgaveForSaksbehandler(
                    område,
                    ReserverForSaksbehandlerDto(OppgaveNøkkelUtenOmrådeDto("oppgave", "type"), "Z999999"),
                    innlogget
                )
            }
        }
    }

    @Test
    fun `uten reserveringstilgang avvises forespørselen før innlesing`() = testApplication {
        setup()
        coEvery { pepClient.harTilgangTilReserveringAvOppgaver() } returns false
        assertEquals(HttpStatusCode.Forbidden, client.post("$sti/k9/reservasjoner/reserver-for-saksbehandler").status)
        verify { tjeneste wasNot Called }
        verify { repository wasNot Called }
    }

    @Test
    fun `blanke parametre avvises før tjenestekall`() = testApplication {
        setup()
        coEvery { pepClient.harTilgangTilReserveringAvOppgaver() } returns true
        for (verdi in listOf("Z999999", "oppgave", "type")) {
            val response = client.post("$sti/k9/reservasjoner/reserver-for-saksbehandler") {
                contentType(ContentType.Application.Json)
                setBody(jsonBody.replace("\"$verdi\"", "\" \""))
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
        }
        verify { tjeneste wasNot Called }
        verify { repository wasNot Called }
    }

    @Test
    fun `tilgangsfeil manglende data og konflikt gir riktige statuskoder`() = testApplication {
        setup()
        coEvery { pepClient.harTilgangTilReserveringAvOppgaver() } returns true
        coEvery { pepClient.harTilgangTilKode6() } returns false
        every { repository.finnSaksbehandlerMedIdent("Z123456", false) } returns innlogget
        for ((feil, status) in listOf(
            ManglerTilgangException("Mangler tilgang") to HttpStatusCode.Forbidden,
            FinnerIkkeDataException("Ikke funnet") to HttpStatusCode.NotFound,
            AlleredeReservertException("Allerede reservert") to HttpStatusCode.Conflict,
        )) {
            coEvery { tjeneste.reserverOppgaveForSaksbehandler(any(), any(), innlogget) } throws feil
            val response = client.post("$sti/k9/reservasjoner/reserver-for-saksbehandler") {
                contentType(ContentType.Application.Json)
                setBody(jsonBody)
            }
            assertEquals(status, response.status)
        }
    }

    private fun TestApplicationBuilder.setup() {
        application {
            install(Koin) {
                modules(module {
                    single { pepClient }
                    single { tjeneste }
                    single { repository }
                    single { RequestContextService(KoinProfile.LOCAL) }
                })
            }
            install(ContentNegotiation) { jackson { findAndRegisterModules() } }
            install(OpenApi)
            routing {
                route(sti) {
                    områdeApi {
                        route("reservasjoner") { ReservasjonApisNy() }
                    }
                }
            }
        }
    }
}
