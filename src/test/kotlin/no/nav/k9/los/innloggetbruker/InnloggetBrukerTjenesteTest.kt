package no.nav.k9.los.innloggetbruker

import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import org.postgresql.util.ServerErrorMessage
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

        tjeneste.vedlikeholdHvisUtdatert(saksbehandler(null), "Z123456", "Saksbehandler Sara", "ny.epost@nav.no")

        coVerify(exactly = 1) {
            repository.vedlikeholdSaksbehandler(
                match { it.id == 1L && it.enhet == "3450" && it.navident == "Z123456" && it.epost == "ny.epost@nav.no" },
                nå
            )
        }
    }

    @Test
    fun `vedlikeholder ikke saksbehandler før det har gått 24 timer`() = runBlocking {
        tjeneste.vedlikeholdHvisUtdatert(saksbehandler(nå.minusHours(23)), "Z123456", "Saksbehandler Sara", "ny.epost@nav.no")

        coVerify(exactly = 0) { azureGraphService.hentEnhetForInnloggetBruker() }
        coVerify(exactly = 0) { repository.vedlikeholdSaksbehandler(any(), any()) }
    }

    @Test
    fun `vedlikeholder ikke saksbehandler når det har gått nøyaktig 24 timer`() = runBlocking {
        tjeneste.vedlikeholdHvisUtdatert(saksbehandler(nå.minusHours(24)), "Z123456", "Saksbehandler Sara", "ny.epost@nav.no")

        coVerify(exactly = 0) { azureGraphService.hentEnhetForInnloggetBruker() }
        coVerify(exactly = 0) { repository.vedlikeholdSaksbehandler(any(), any()) }
    }

    @Test
    fun `vedlikeholder saksbehandler når det har gått mer enn 24 timer`() = runBlocking {
        coEvery { azureGraphService.hentEnhetForInnloggetBruker() } returns "3450"

        tjeneste.vedlikeholdHvisUtdatert(saksbehandler(nå.minusHours(25)), "Z123456", "Saksbehandler Sara", "ny.epost@nav.no")

        coVerify(exactly = 1) { repository.vedlikeholdSaksbehandler(any(), nå) }
    }

    @Test
    fun `vedlikeholder ikke saksbehandler når Azure er utilgjengelig (prøver igjen ved neste kall)`() = runBlocking {
        coEvery { azureGraphService.hentEnhetForInnloggetBruker() } throws IllegalStateException("Azure er utilgjengelig")

        tjeneste.vedlikeholdHvisUtdatert(saksbehandler(nå.minusDays(2)), "Z123456", "Saksbehandler Sara", "ny.epost@nav.no")

        coVerify(exactly = 0) { repository.vedlikeholdSaksbehandler(any(), any()) }
    }

    @Test
    fun `epostkonflikt avbryter ikke innlogging og vedlikehold forsokes igjen`() = runBlocking {
        coEvery { azureGraphService.hentEnhetForInnloggetBruker() } returns "3450"
        coEvery { repository.vedlikeholdSaksbehandler(any(), any()) } throws
            PSQLException(ServerErrorMessage("C23505\u0000nsaksbehandler_epost_key\u0000"))
        val opprinnelig = saksbehandler(null)

        repeat(2) {
            tjeneste.vedlikeholdHvisUtdatert(opprinnelig, "Z123456", "Saksbehandler Sara", "ny.epost@nav.no")
        }

        coVerify(exactly = 2) { repository.vedlikeholdSaksbehandler(match { it.id == opprinnelig.id }, nå) }
    }

    @Test
    fun `andre databasefeil kastes videre`() = runBlocking {
        coEvery { azureGraphService.hentEnhetForInnloggetBruker() } returns "3450"
        listOf(
            "23505" to "saksbehandler_id_key",
            "23503" to "saksbehandler_epost_key",
            "23505" to null
        ).forEach { (sqlState, constraint) ->
            val feil = PSQLException(ServerErrorMessage("C$sqlState\u0000" + (constraint?.let { "n$it\u0000" } ?: "")))
            coEvery { repository.vedlikeholdSaksbehandler(any(), any()) } throws feil

            val kastet = assertThrows<PSQLException> {
                tjeneste.vedlikeholdHvisUtdatert(saksbehandler(null), "Z123456", "Saksbehandler Sara", "ny.epost@nav.no")
            }

            assertSame(feil, kastet)
        }
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
