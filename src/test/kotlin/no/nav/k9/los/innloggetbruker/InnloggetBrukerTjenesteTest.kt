package no.nav.k9.los.innloggetbruker

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

class InnloggetBrukerTjenesteTest {
    private val nå = LocalDateTime.parse("2026-08-28T10:00:00")
    private val clock = Clock.fixed(nå.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val repository = mockk<SaksbehandlerRepository>(relaxed = true)
    private val azureGraphService = mockk<IAzureGraphService>()
    private val tjeneste = InnloggetBrukerTjeneste(repository, azureGraphService, clock)

    @Test
    fun `vedlikeholder saksbehandler når tidspunkt mangler`() = runBlocking {
        coEvery { azureGraphService.hentEnhetForInnloggetBruker() } returns "3450"

        tjeneste.vedlikeholdHvisUtdatert(saksbehandler(null), "Z123456", "Saksbehandler Sara")

        coVerify(exactly = 1) {
            repository.vedlikeholdSaksbehandler(
                match { it.enhet == "3450" && it.navident == "Z123456" },
                nå
            )
        }
    }

    @Test
    fun `vedlikeholder ikke saksbehandler før det har gått 24 timer`() = runBlocking {
        tjeneste.vedlikeholdHvisUtdatert(saksbehandler(nå.minusHours(23)), "Z123456", "Saksbehandler Sara")

        coVerify(exactly = 0) { azureGraphService.hentEnhetForInnloggetBruker() }
        coVerify(exactly = 0) { repository.vedlikeholdSaksbehandler(any(), any()) }
    }

    @Test
    fun `vedlikeholder ikke saksbehandler når det har gått nøyaktig 24 timer`() = runBlocking {
        tjeneste.vedlikeholdHvisUtdatert(saksbehandler(nå.minusHours(24)), "Z123456", "Saksbehandler Sara")

        coVerify(exactly = 0) { azureGraphService.hentEnhetForInnloggetBruker() }
        coVerify(exactly = 0) { repository.vedlikeholdSaksbehandler(any(), any()) }
    }

    @Test
    fun `vedlikeholder saksbehandler når det har gått mer enn 24 timer`() = runBlocking {
        coEvery { azureGraphService.hentEnhetForInnloggetBruker() } returns "3450"

        tjeneste.vedlikeholdHvisUtdatert(saksbehandler(nå.minusHours(25)), "Z123456", "Saksbehandler Sara")

        coVerify(exactly = 1) { repository.vedlikeholdSaksbehandler(any(), nå) }
    }

    @Test
    fun `forsøker på nytt etter feil fra Azure`() = runBlocking {
        coEvery { azureGraphService.hentEnhetForInnloggetBruker() } throws IllegalStateException("Azure er utilgjengelig")

        tjeneste.vedlikeholdHvisUtdatert(saksbehandler(nå.minusDays(2)), "Z123456", "Saksbehandler Sara")

        coVerify(exactly = 0) { repository.vedlikeholdSaksbehandler(any(), any()) }
    }

    private fun saksbehandler(sistOppdatert: LocalDateTime?) = Saksbehandler(
        id = 1,
        navident = "Z123456",
        navn = "Saksbehandler Sara",
        epost = "saksbehandler@nav.no",
        enhet = "3450",
        sistOppdatert = sistOppdatert
    )
}
