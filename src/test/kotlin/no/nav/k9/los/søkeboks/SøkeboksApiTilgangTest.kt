package no.nav.k9.los.søkeboks

import io.ktor.client.request.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstFactory
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import no.nav.k9.los.områdeApi
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals

class SøkeboksApiTilgangTest {
    @Test
    fun `AP ruter svarer kontrollert uten å lese body eller kalle søketjeneste`() = testApplication {
        val factory = mockk<BrukerkontekstFactory>()
        coEvery { factory.medOmråde(Områder.AKTIVITETSPENGER, any()) } returns
            TestKontekstFactory.brukerkontekst(Områder.AKTIVITETSPENGER)
        val tjeneste = mockk<SøkeboksTjeneste>()
        application {
            install(Koin) {
                modules(module {
                    single { KoinProfile.LOCAL }
                    single { factory }
                    single { tjeneste }
                })
            }
            routing {
                områdeApi(Områder.AKTIVITETSPENGER) {
                    route("legacy") { SøkeboksApi() }
                    route("ny") { SøkeboksApiNy() }
                }
            }
        }
        assertEquals(HttpStatusCode.NotImplemented, client.post("/legacy").status)
        assertEquals(HttpStatusCode.NotImplemented, client.post("/ny").status)
        coVerify { tjeneste wasNot Called }
    }
}
