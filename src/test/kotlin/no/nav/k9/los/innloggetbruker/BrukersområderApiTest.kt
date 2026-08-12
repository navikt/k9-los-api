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
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals

class BrukersområderApiTest : AbstractPostgresTest() {

    @Test
    fun `returnerer innlogget brukers områder når bruker finnes på navident`() {
        val områdeRepository = OmrådeRepository(dataSource)
        områdeRepository.lagre(Områder.UNG.eksternId)

        val saksbehandlerRepository = saksbehandlerRepository()

        runBlocking {
            saksbehandlerRepository.addSaksbehandler("saksbehandler@nav.no", Områder.K9)
            saksbehandlerRepository.addSaksbehandler("saksbehandler@nav.no", Områder.UNG)
            saksbehandlerRepository.vedlikeholdSaksbehandler(
                Saksbehandler(
                    id = null,
                    navident = "Z123456",
                    navn = "Saksbehandler Sara",
                    epost = "saksbehandler@nav.no",
                    enhet = "3450",
                    områder = listOf(Områder.K9, Områder.UNG)
                )
            )
        }

        testApplication {
            application {
                testApp()
            }

            val response = client.get("/brukersområder")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("[\"K9\",\"UNG\"]", response.bodyAsText())
        }
    }

    @Test
    fun `returnerer innlogget brukers områder når bruker kun finnes på epost`() {
        val saksbehandlerRepository = saksbehandlerRepository()

        runBlocking {
            saksbehandlerRepository.addSaksbehandler("saksbehandler@nav.no", Områder.K9)
        }

        testApplication {
            application {
                testApp()
            }

            val response = client.get("/brukersområder")

            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals("[\"K9\"]", response.bodyAsText())
        }
    }

    private fun saksbehandlerRepository(): SaksbehandlerRepository {
        val pepClient = PepClientLocal()
        val områdeRepository = OmrådeRepository(dataSource)
        return SaksbehandlerRepository(
            dataSource = dataSource,
            pepClient = pepClient,
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
                    single<IPepClient> { PepClientLocal() }
                    single { OmrådeRepository(dataSource) }
                    single { TransactionalManager(dataSource) }
                    single {
                        SaksbehandlerRepository(
                            dataSource = dataSource,
                            pepClient = get(),
                            transactionalManager = get(),
                            områdeRepository = get(),
                        )
                    }
                    single { RequestContextService(profile = KoinProfile.LOCAL) }
                }
            )
        }

        routing {
            route("brukersområder") {
                BrukersområderApi()
            }
        }
    }
}
