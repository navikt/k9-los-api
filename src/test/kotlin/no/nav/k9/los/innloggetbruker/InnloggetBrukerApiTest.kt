package no.nav.k9.los.innloggetbruker

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.jackson.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractPostgresTest
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.abac.PepClientLocal
import no.nav.k9.los.infrastruktur.azuregraph.AzureGraphServiceLocal
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.områdeApi
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.time.Clock
import kotlin.test.assertContains
import kotlin.test.assertEquals

class InnloggetBrukerApiTest : AbstractPostgresTest() {

    @Test
    fun `returnerer alle områder PDP gir basistilgang til`() {
        val områdeRepository = OmrådeRepository(dataSource)
        områdeRepository.lagre(Områder.AKTIVITETSPENGER.eksternId)

        val saksbehandlerRepository = saksbehandlerRepository()

        runBlocking {
            saksbehandlerRepository.addSaksbehandler("saksbehandler@nav.no", Områder.K9)
            saksbehandlerRepository.addSaksbehandler("saksbehandler@nav.no", Områder.AKTIVITETSPENGER)
            saksbehandlerRepository.vedlikeholdSaksbehandler(
                Saksbehandler(
                    id = null,
                    navident = "Z123456",
                    navn = "Saksbehandler Sara",
                    epost = "saksbehandler@nav.no",
                    enhet = "3450",
                    områder = listOf(Områder.K9, Områder.AKTIVITETSPENGER),
                    kode6 = false
                ),
                skjermet = false,
            )
        }

        testApplication {
            application {
                testApp()
            }

            val response = client.get("/innlogget-bruker/områder")

            assertEquals(HttpStatusCode.OK, response.status)
            val bodyAsText = response.bodyAsText()
            assertContains(bodyAsText, "AKTIVITETSPENGER")
            assertContains(bodyAsText, "K9")
        }
    }

    @Test
    fun `områdelisten er uavhengig av registrerte områder`() {
        val saksbehandlerRepository = saksbehandlerRepository()

        runBlocking {
            saksbehandlerRepository.addSaksbehandler("saksbehandler@nav.no", Områder.K9)
        }

        testApplication {
            application {
                testApp()
            }

            val response = client.get("/innlogget-bruker/områder")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("[\"K9\",\"AKTIVITETSPENGER\"]", response.bodyAsText())
        }
    }

    @Test
    fun `områdespesifikt endepunkt returnerer innlogget bruker`() {
        val saksbehandlerRepository = saksbehandlerRepository()
        runBlocking {
            saksbehandlerRepository.addSaksbehandler("saksbehandler@nav.no", Områder.K9)
        }

        testApplication {
            application { testApp() }

            val response = client.get("/k9/innlogget-bruker")

            assertEquals(HttpStatusCode.OK, response.status)
            val body = response.bodyAsText()
            assertContains(body, "\"harBasisTilgang\":true")
        }
    }

    private fun saksbehandlerRepository(): SaksbehandlerRepository {
        val områdeRepository = OmrådeRepository(dataSource)
        return SaksbehandlerRepository(
            dataSource = dataSource,
            transactionalManager = TransactionalManager(dataSource),
            områdeRepository = områdeRepository,
        )
    }

    private fun Application.testApp() {
        install(ContentNegotiation) {
            jackson()
        }

        install(Koin) {
            modules(
                module {
                    single { KoinProfile.LOCAL }
                    single<IPepClient> { PepClientLocal() }
                    single { no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstFactory(lokaleTilganger = true) }
                    single<IAzureGraphService> { AzureGraphServiceLocal() }
                    single { Clock.systemDefaultZone() }
                    single { OmrådeRepository(dataSource) }
                    single { TransactionalManager(dataSource) }
                    single {
                        SaksbehandlerRepository(
                            dataSource = dataSource,
                            transactionalManager = get(),
                            områdeRepository = get(),
                        )
                    }
                    single { InnloggetBrukerTjeneste(get(), get(), get()) }
                }
            )
        }

        routing {
            route("innlogget-bruker/områder") {
                InnloggetBrukersOmråderApi()
            }
            områdeApi {
                route("innlogget-bruker") {
                    InnloggetBrukerApi()
                }
            }
        }
    }
}
