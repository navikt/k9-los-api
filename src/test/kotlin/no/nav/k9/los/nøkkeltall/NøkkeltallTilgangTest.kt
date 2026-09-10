package no.nav.k9.los.nøkkeltall

import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.jackson
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstFactory
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import no.nav.k9.los.nøkkeltall.avdelingsleder.dagenstall.DagensTallService
import no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet.FerdigstiltePerEnhetService
import no.nav.k9.los.nøkkeltall.avdelingsleder.status.StatusService
import no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.StatusFordelingService
import no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte.NyeOgFerdigstilteApi
import no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte.NyeOgFerdigstilteApiNy
import no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte.NyeOgFerdigstilteService
import no.nav.k9.los.områdeApi
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NøkkeltallTilgangTest {
    @Test
    fun `AP avvises uten K9 tjenester`() = kontrollerRuter(Områder.AKTIVITETSPENGER, true, HttpStatusCode.NotImplemented)

    @Test
    fun `K9 uten rettigheter avvises`() = kontrollerRuter(Områder.K9, false, HttpStatusCode.Forbidden)

    @Test
    fun `K9 uten kode6 beholder tilgang til systemjobbtall`() = kontrollerRuter(Områder.K9, true, HttpStatusCode.OK)

    private fun kontrollerRuter(område: Områder, tilgang: Boolean, forventet: HttpStatusCode) = testApplication {
        val query = mockk<OppgaveQueryService>()
        every { query.query(any(), any()) } returns emptyList()
        every { query.queryForAntall(any(), any()) } returns 0L
        val factory = mockk<BrukerkontekstFactory>()
        coEvery { factory.medOmråde(område, any()) } returns TestKontekstFactory.brukerkontekst(
            område, tilganger = if (tilgang) TestKontekstFactory.ALLE_TILGANGER else TestKontekstFactory.INGEN_TILGANGER,
        )
        application {
            install(ContentNegotiation) { jackson { findAndRegisterModules() } }
            install(Koin) {
                modules(module {
                    single { KoinProfile.LOCAL }
                    single { factory }
                    if (forventet == HttpStatusCode.OK) {
                        single { StatusService(query) }
                        single { StatusFordelingService(query) }
                        single { DagensTallService(query) }
                        single { FerdigstiltePerEnhetService(query) }
                        single { NyeOgFerdigstilteService(query) }
                    }
                })
            }
            routing {
                områdeApi(område) {
                    route("legacy") {
                        NøkkeltallV3Apis()
                        route("nye") { NyeOgFerdigstilteApi() }
                    }
                    route("ny") {
                        NøkkeltallV3ApisNy()
                        route("nye") { NyeOgFerdigstilteApiNy() }
                    }
                }
            }
        }
        for (api in listOf("legacy", "ny")) {
            for (sti in listOf("status", "statusfordeling", "dagens-tall", "ferdigstilte-per-enhet", "nye")) {
                assertEquals(forventet, client.get("/$api/$sti").status, "$api/$sti")
            }
        }
        if (forventet != HttpStatusCode.OK) verify { query wasNot Called }
    }

    @Test
    fun `status og statusfordeling bevarer kode6 regler og sender querytilgang`() {
        for (kode6 in listOf(false, true)) {
            val requests = mutableListOf<QueryRequest>()
            val query = mockk<OppgaveQueryService>()
            every { query.query(capture(requests), any()) } returns emptyList()
            every { query.queryForAntall(capture(requests), any()) } returns 0L
            StatusService(query).hentStatus(kode6)
            assertEquals(kode6, requests.single().harTilgangTilKode6)
            val statusfiltre = requests.single().oppgaveQuery.filtere.filterIsInstance<FeltverdiOppgavefilter>()
            assertEquals(!kode6, statusfiltre.any { it.kode == "personbeskyttelse" })
            requests.clear()
            StatusFordelingService(query).hentVerdi(kode6)
            assertTrue(requests.isNotEmpty())
            assertTrue(requests.all { it.harTilgangTilKode6 == kode6 && it.område == Områder.K9 })
            assertTrue(requests.all { request ->
                request.oppgaveQuery.filtere.filterIsInstance<FeltverdiOppgavefilter>().any {
                    it.kode == "personbeskyttelse" && it.verdi == listOf(if (kode6) "KODE6" else "UTEN_KODE6")
                }
            })
        }
    }
}
