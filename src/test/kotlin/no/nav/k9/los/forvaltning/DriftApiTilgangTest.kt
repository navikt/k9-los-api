package no.nav.k9.los.forvaltning

import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.domeneadaptere.eventlager.EventlagerApi
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventlagerApiNy
import no.nav.k9.los.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.domeneadaptere.k9.statistikk.OppgavestatistikkTjeneste
import no.nav.k9.los.domeneadaptere.k9.statistikk.StatistikkApi
import no.nav.k9.los.domeneadaptere.k9.statistikk.StatistikkApiNy
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstFactory
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.områdeApi
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals

class DriftApiTilgangTest {
    @Test
    fun `manglende drift gir ingen bestillinger eller bakgrunnsjobber i begge APIer`() {
        kontrollerAvslag(Områder.K9, drift = false)
    }

    @Test
    fun `AP drift kan ikke kjøre K9 operasjoner i begge APIer`() {
        kontrollerAvslag(Områder.AKTIVITETSPENGER, drift = true)
    }

    @Test
    fun `eventets lagrede område kontrolleres før bestilling`() = testApplication {
        val events = mockk<EventRepository>()
        val forvaltning = mockk<ForvaltningRepository>()
        every { forvaltning.krevEventområde(any(), any(), any()) } throws SecurityException("Feil område")
        val factory = mockk<BrukerkontekstFactory>()
        coEvery { factory.medOmråde(any(), any()) } returns BrukerkontekstMedOmråde(
            område = Områder.K9,
            navIdent = "Z123456",
            idToken = IdTokenLocal(),
            harBasisTilgang = true,
            harTilgangTilKode6 = false,
            erOppgavestyrer = false,
            harTilgangTilReserveringAvOppgaver = false,
            harDriftstilgang = true,
        )
        application {
            install(Koin) {
                modules(module {
                    single { KoinProfile.LOCAL }
                    single { factory }
                    single { events }
                    single { forvaltning }
                })
            }
            routing {
                områdeApi(Områder.K9) {
                    route("legacy") { EventlagerApi() }
                    route("ny") { EventlagerApiNy() }
                }
            }
        }
        for (api in listOf("legacy", "ny")) {
            assertEquals(HttpStatusCode.Forbidden, client.request("/$api/K9SAK/bestillHistorikkvask") {
                method = HttpMethod.Put
            }.status)
        }
        verify(exactly = 2) { forvaltning.krevEventområde(any(), Områder.K9, null) }
        verify { events wasNot Called }
    }

    private fun kontrollerAvslag(område: Områder, drift: Boolean) = testApplication {
        val events = mockk<EventRepository>()
        val adapter = mockk<EventTilOppgaveAdapter>()
        val statistikk = mockk<OppgavestatistikkTjeneste>()
        val forvaltning = mockk<ForvaltningRepository>()
        val factory = mockk<BrukerkontekstFactory>()
        coEvery { factory.medOmråde(any(), any()) } returns BrukerkontekstMedOmråde(
            område = område,
            navIdent = "Z123456",
            idToken = IdTokenLocal(),
            harBasisTilgang = true,
            harTilgangTilKode6 = false,
            erOppgavestyrer = false,
            harTilgangTilReserveringAvOppgaver = false,
            harDriftstilgang = drift,
        )
        application {
            install(Koin) {
                modules(module {
                    single { KoinProfile.LOCAL }
                    single { factory }
                    single { events }
                    single { adapter }
                    single { statistikk }
                    single { forvaltning }
                })
            }
            routing {
                områdeApi(område) {
                    route("legacy/event") { EventlagerApi() }
                    route("ny/event") { EventlagerApiNy() }
                    route("legacy/statistikk") { StatistikkApi() }
                    route("ny/statistikk") { StatistikkApiNy() }
                    route("legacy/forvaltning") { forvaltningApis() }
                    route("ny/forvaltning") { forvaltningApisNy() }
                }
            }
        }
        for (api in listOf("legacy", "ny")) {
            for ((metode, sti) in listOf(
                HttpMethod.Get to "event/eventer/K9SAK/123",
                HttpMethod.Put to "event/spillAvDirtyEventer",
                HttpMethod.Put to "event/K9SAK/bestillHistorikkvask",
                HttpMethod.Put to "event/bestillHistorikkvaskForEnkeltoppgave?fagsystem=K9SAK&eksternId=123",
                HttpMethod.Put to "statistikk",
                HttpMethod.Get to "statistikk/resendStatistikkFraStart/k9sak",
                HttpMethod.Post to "forvaltning/bestillHistorikkvaskFraQuery/K9SAK",
                HttpMethod.Post to "forvaltning/bestillDvhSendingFraQuery/k9sak",
                HttpMethod.Get to "forvaltning/avstemming/K9SAK",
                HttpMethod.Get to "forvaltning/K9SAK/123/finnEksternId"
            )) {
                assertEquals(HttpStatusCode.Forbidden, client.request("/$api/$sti") { method = metode }.status, sti)
            }
        }
        verify { listOf(events, adapter, statistikk, forvaltning) wasNot Called }
    }
}
