package no.nav.k9.los.uttrekk

import io.ktor.client.request.request
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.install
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.ktor.serialization.jackson.jackson
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.justRun
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstFactory
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.idtoken.IdTokenLocal
import no.nav.k9.los.lagretsok.LagretSøk
import no.nav.k9.los.lagretsok.LagretSøkRepository
import no.nav.k9.los.områdeApi
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Test
import org.koin.dsl.module
import org.koin.ktor.plugin.Koin
import java.time.LocalDateTime
import kotlin.test.assertEquals

class UttrekkApiTilgangTest {
    @Test
    fun `begge APIer krever kjent og samsvarende kode6 ved CSV og JSON nedlasting`() = testApplication {
        val repository = mockk<UttrekkRepository>()
        val søkRepository = mockk<LagretSøkRepository>()
        val saksbehandlere = mockk<SaksbehandlerRepository>()
        val saksbehandler = mockk<Saksbehandler> { every { id } returns 1 }
        coEvery { saksbehandlere.finnSaksbehandlerMedIdent(any(), any()) } returns saksbehandler
        var lagretKode6: Boolean? = null
        var currentKode6 = false
        every { repository.hent(20) } answers {
            Uttrekk.fraEksisterende(
                id = 20, opprettetTidspunkt = LocalDateTime.now(), status = UttrekkStatus.FULLFØRT,
                tittel = "Test", query = OppgaveQuery(), lagetAv = 1, lagretSøkId = null,
                limit = null, offset = null, feilmelding = null, startetTidspunkt = null,
                fullførtTidspunkt = LocalDateTime.now(), antall = 0, harTilgangTilKode6 = lagretKode6,
            )
        }
        every { repository.hentResultat(20) } returns "[]"
        justRun { repository.slett(20) }
        val factory = mockk<BrukerkontekstFactory>()
        coEvery { factory.medOmråde(any(), any()) } answers {
            BrukerkontekstMedOmråde(
                område = Områder.K9,
                navIdent = "Z123456",
                idToken = IdTokenLocal(),
                harBasisTilgang = true,
                harTilgangTilKode6 = currentKode6,
                erOppgavestyrer = true,
                harTilgangTilReserveringAvOppgaver = true,
                harDriftstilgang = true,
            )
        }
        application {
            install(ContentNegotiation) { jackson { registerModule(JavaTimeModule()) } }
            install(Koin) {
                modules(module {
                    single { KoinProfile.LOCAL }
                    single { factory }
                    single { repository }
                    single { UttrekkTjeneste(repository, søkRepository) }
                    single { saksbehandlere }
                    single { UttrekkCsvGenerator() }
                })
            }
            routing {
                områdeApi(Områder.K9) {
                    route("legacy") { UttrekkApi() }
                    route("ny") { UttrekkApiNy() }
                }
            }
        }
        for (api in listOf("legacy", "ny")) {
            for (lagret in listOf(null, false, true)) {
                lagretKode6 = lagret
                for (current in listOf(false, true)) {
                    currentKode6 = current
                    val forventet = if (lagret != null && lagret == current) HttpStatusCode.OK else HttpStatusCode.Forbidden
                    for (format in listOf("csv", "json")) {
                        assertEquals(forventet, client.request("/$api/20/$format").status, "$api $format lagret=$lagret current=$current")
                    }
                }
            }
            lagretKode6 = null
            // Ukjent gammelt beskyttelsesnivå hindrer ikke eieren i å administrere uttrekket.
            assertEquals(HttpStatusCode.OK, client.request("/$api/20").status)
            assertEquals(HttpStatusCode.OK, client.request("/$api/20/slett") { method = HttpMethod.Delete }.status)
        }
        verify(exactly = 8) { repository.hentResultat(20) }
        verify { søkRepository wasNot Called }
    }

    @Test
    fun `alle ID ruter avviser annen eier uten resultatlesing eller mutasjon`() {
        kontrollerAvslag(Områder.K9, eier = 2)
    }

    @Test
    fun `alle ID ruter avviser K9 uttrekk på AP route`() {
        kontrollerAvslag(Områder.AKTIVITETSPENGER, eier = 1)
    }

    @Test
    fun `alle direkte ID ruter avviser endret kode6 nivå`() {
        kontrollerAvslag(Områder.K9, eier = 1, lagretKode6 = true)
    }

    private fun kontrollerAvslag(område: Områder, eier: Long, lagretKode6: Boolean = false) = testApplication {
        val repository = mockk<UttrekkRepository>()
        val søkRepository = mockk<LagretSøkRepository>()
        val saksbehandlere = mockk<SaksbehandlerRepository>()
        val saksbehandler = mockk<Saksbehandler> { every { id } returns 1 }
        coEvery { saksbehandlere.finnSaksbehandlerMedIdent(any(), any()) } returns saksbehandler
        val søk = LagretSøk.fraEksisterende(10, eier, Områder.K9, 1, "Test", "", LocalDateTime.now())
        every { repository.hent(20) } returns Uttrekk.opprettUttrekk(søk, eier, lagretKode6)
        every { søkRepository.hent(10) } returns søk
        val factory = mockk<BrukerkontekstFactory>()
        coEvery { factory.medOmråde(any(), any()) } returns BrukerkontekstMedOmråde(
            område = område,
            navIdent = "Z123456",
            idToken = IdTokenLocal(),
            harBasisTilgang = true,
            harTilgangTilKode6 = false,
            erOppgavestyrer = true,
            harTilgangTilReserveringAvOppgaver = true,
            harDriftstilgang = true,
        )
        application {
            install(Koin) {
                modules(module {
                    single { KoinProfile.LOCAL }
                    single { factory }
                    single { repository }
                    single { UttrekkTjeneste(repository, søkRepository) }
                    single { saksbehandlere }
                })
            }
            routing {
                områdeApi(område) {
                    route("legacy") { UttrekkApi() }
                    route("ny") { UttrekkApiNy() }
                }
            }
        }
        for (api in listOf("legacy", "ny")) {
            for ((metode, sti) in listOf(
                HttpMethod.Get to "20",
                HttpMethod.Get to "20/csv",
                HttpMethod.Get to "20/json",
                HttpMethod.Put to "20/tittel",
                HttpMethod.Delete to "20/slett",
            )) {
                assertEquals(HttpStatusCode.Forbidden, client.request("/$api/$sti") { method = metode }.status, sti)
            }
        }
        verify(exactly = 0) { repository.hentResultat(any()) }
        verify(exactly = 0) { repository.oppdater(any(), any()) }
        verify(exactly = 0) { repository.slett(any()) }
        verify(exactly = 0) { repository.slettForLagretSøk(any(), any()) }
    }
}
