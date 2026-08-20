package no.nav.k9.los.apis

import com.github.tomakehurst.wiremock.WireMockServer
import io.ktor.client.HttpClient
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.helse.dusseldorf.ktor.auth.*
import no.nav.helse.dusseldorf.testsupport.jws.Azure
import no.nav.helse.dusseldorf.testsupport.wiremock.WireMockBuilder
import no.nav.helse.dusseldorf.testsupport.wiremock.getAzureV2JwksUrl
import no.nav.k9.los.AbstractPostgresTest
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.driftsmelding.DriftsmeldingRepository
import no.nav.k9.los.driftsmelding.DriftsmeldingTjeneste
import no.nav.k9.los.driftsmelding.DriftsmeldingerApis
import no.nav.k9.los.infrastruktur.abac.Gruppeoppsett
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.abac.OmrådeIkkeTilgjengeligException
import no.nav.k9.los.infrastruktur.abac.PepClient
import no.nav.k9.los.infrastruktur.abac.SifAbacPdpKlienter
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.innloggetbruker.BrukersområderApi
import no.nav.k9.los.innloggetbruker.InnloggetBrukerApi
import no.nav.k9.los.områdeApi
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.net.URI
import javax.sql.DataSource
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Røyktest som kaller ruter med gyldig token og asserter at ingen svarer 500 fordi
 * konteksten (idToken/område) ikke er satt.
 *
 * Feiler på branch-tilstanden før de tre fiksene:
 * 1. GET /innloggetbruker/saksbehandler leste idToken fra engine-konteksten (IllegalStateException)
 * 2. GET /brukersområder og GET /driftsmeldinger er registrert utenfor områdeApi {} men traff
 *    områdeavhengig tilgangskontroll (IllegalStateException "Område er ikke satt")
 * 3. GET /driftsmeldinger krevde harBasisTilgang() for området selv om driftsmeldinger er globale
 *
 * Rot-rutetreet (K9Los.apiUnderConstruction) er ikke tilgjengelig fra test-scope, så rutene
 * registreres med samme struktur som i K9Los.kt:301-304 — utenfor områdeApi.
 */
internal class RuteSmokeTest : AbstractPostgresTest() {

    @Test
    fun `ruter utenfor områdeApi svarer ikke 500 med gyldig token`() = medTestApp { app ->
        val authorization = authorizationHeader(username = "saksbehandler@nav.no", navIdent = "Z123456")

        val innlogget = app.client.get("/k9/innloggetbruker/saksbehandler") {
            header(HttpHeaders.Authorization, authorization)
        }
        assertNotEquals(
            HttpStatusCode.InternalServerError, innlogget.status,
            "GET /k9/innloggetbruker/saksbehandler ga 500: ${innlogget.bodyAsText()}"
        )

        val brukersområder = app.client.get("/brukersområder") {
            header(HttpHeaders.Authorization, authorization)
        }
        assertNotEquals(
            HttpStatusCode.InternalServerError, brukersområder.status,
            "GET /brukersområder ga 500: ${brukersområder.bodyAsText()}"
        )

        val driftsmeldinger = app.client.get("/driftsmeldinger") {
            header(HttpHeaders.Authorization, authorization)
        }
        assertNotEquals(
            HttpStatusCode.InternalServerError, driftsmeldinger.status,
            "GET /driftsmeldinger ga 500: ${driftsmeldinger.bodyAsText()}"
        )

        // Uten basistilgang i noen område er 403 forventet fra driftsmeldinger — poenget er at
        // konteksten er korrekt etablert (ikke 500), ikke at tilgang gis.
        assertEquals(HttpStatusCode.OK, brukersområder.status)
        assertEquals(HttpStatusCode.Forbidden, driftsmeldinger.status)
    }

    @Test
    fun `saksbehandleroppslag bruker globale erKode6Bruker og finner raden uavhengig av rute-område`() = medTestApp { app ->
        // Registrer en saksbehandler-rad. Repositoryet slår opp raden med skjermet-flagg fra
        // erKode6Bruker() (global, union over alle områder) — ikke harTilgangTilKode6() for
        // rute-området. Før fiksen ville en kode6-konto på en rute uten kode6-tilgang ikke
        // funnet sin egen rad.
        val epost = "kode6@nav.no"
        kotlinx.coroutines.runBlocking {
            app.saksbehandlerRepository.addSaksbehandler(epost, Områder.K9)
        }

        val response = app.client.get("/brukersområder") {
            header(HttpHeaders.Authorization, authorizationHeader(username = epost, navIdent = "Z654321"))
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("[\"K9\"]", response.bodyAsText())
    }

    private fun medTestApp(test: suspend (TestApp) -> Unit) {
        val wireMock: WireMockServer = WireMockBuilder().withAzureSupport().build()
        val testApp = TestApp(dataSource, wireMock)
        try {
            testApplication {
                application { testApp.install(this) }
                testApp.client = client
                test(testApp)
            }
        } finally {
            wireMock.stop()
        }
    }

    private class TestApp(dataSource: DataSource, private val wireMock: WireMockServer) {
        lateinit var client: HttpClient

        val pepClient = PepClient(mockk<IAzureGraphService>(), mockk<SifAbacPdpKlienter>(), Gruppeoppsett())
        val områdeRepository = OmrådeRepository(dataSource)
        val saksbehandlerRepository = SaksbehandlerRepository(
            dataSource = dataSource,
            transactionalManager = TransactionalManager(dataSource),
            områdeRepository = områdeRepository,
        )
        private val transactionalManager = TransactionalManager(dataSource)

        fun install(application: Application) = with(application) {
            val issuer = Issuer(
                issuer = Azure.V2_0.getIssuer(),
                jwksUri = URI(wireMock.getAzureV2JwksUrl()),
                audience = "k9-los-api",
                alias = "azure-v2",
            )
            val issuers = mapOf(issuer.alias() to issuer).withoutAdditionalClaimRules()
            install(Authentication) { multipleJwtIssuers(issuers) }
            install(ContentNegotiation) { jackson() }
            install(StatusPages) {
                AuthStatusPages()
                exception<OmrådeIkkeTilgjengeligException> { call, _ ->
                    call.respond(HttpStatusCode.Forbidden)
                }
            }

            // Real PepClient mot tom gruppeoppsett (ingen env-variabler satt -> alle gruppe-IDer null).
            // Med token uten grupper gir det harTilgangTilKode6()=false, erKode6Bruker()=false,
            // harBasisTilgang()=false. Testene verifiserer at spørsmålene besvares uten å kaste,
            // ikke at tilgang gis.
            install(Koin) {
                modules(
                    module {
                        single<IPepClient> { pepClient }
                        single { saksbehandlerRepository }
                        single { områdeRepository }
                        single { transactionalManager }
                        single { RequestContextService(profile = KoinProfile.PROD) }
                        single { DriftsmeldingRepository(get()) }
                        single { DriftsmeldingTjeneste(get()) }
                        single<IAzureGraphService> {
                            mockk {
                                coEvery { hentIdentTilInnloggetBruker() } returns "Z123456"
                                coEvery { hentEnhetForInnloggetBruker() } returns "3450"
                            }
                        }
                        single {
                            mockk<Configuration>().also { config ->
                                every { config.koinProfile() } returns KoinProfile.PROD
                            }
                        }
                    }
                )
            }

            routing {
                authenticate(*issuers.allIssuers()) {
                    // Samme struktur som K9Los.kt:301-304 — driftsmeldinger og brukersområder utenfor områdeApi
                    route("driftsmeldinger") { DriftsmeldingerApis() }
                    route("brukersområder") { BrukersområderApi() }
                    områdeApi {
                        route("innloggetbruker") { InnloggetBrukerApi() }
                    }
                }
            }
        }
    }
}
