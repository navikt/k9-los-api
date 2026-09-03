package no.nav.k9.los.innloggetbruker

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.*
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import org.postgresql.util.ServerErrorMessage
import java.time.Clock
import java.time.LocalDateTime
import java.time.ZoneOffset

class InnloggetBrukerTjenesteTest {
    private val nå = LocalDateTime.parse("2026-08-28T10:00:00")
    private val clock = Clock.fixed(nå.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
    private val repository = mockk<SaksbehandlerRepository>()
    private val azureGraphService = mockk<IAzureGraphService>()
    private val tjeneste = InnloggetBrukerTjeneste(repository, azureGraphService, clock)
    private val bruker = TestKontekstFactory.brukerkontekst(Områder.K9)
    private val områdeløsBruker = TestKontekstFactory.brukerkontekstUtenOmråde()

    @Test
    fun `vedlikeholder saksbehandler når tidspunkt mangler`() = runBlocking {
        val saksbehandler = saksbehandler(sistOppdatert = null)
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns saksbehandler
        coEvery { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) } returns "3450"
        every { repository.vedlikeholdSaksbehandler(any(), any(), any()) } returns 1

        val resultat = tjeneste.hentInnloggetBruker(bruker)

        assertThat(resultat.brukerIdent).isEqualTo("Z123456")
        coVerify(exactly = 1) { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) }
        verify(exactly = 1) {
            repository.vedlikeholdSaksbehandler(
                match { it.enhet == "3450" && it.navident == "Z123456" },
                skjermet = false,
                oppdatertTidspunkt = nå,
            )
        }
    }

    @Test
    fun `vedlikeholder ikke saksbehandler før det har gått 24 timer`() = runBlocking {
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns
            saksbehandler(sistOppdatert = nå.minusHours(23))

        tjeneste.hentInnloggetBruker(bruker)

        coVerify(exactly = 0) { azureGraphService.hentEnhet(any(), any()) }
        verify(exactly = 0) { repository.vedlikeholdSaksbehandler(any(), any(), any()) }
    }

    @Test
    fun `vedlikeholder ikke saksbehandler når det har gått nøyaktig 24 timer`() = runBlocking {
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns
            saksbehandler(sistOppdatert = nå.minusHours(24))

        tjeneste.hentInnloggetBruker(bruker)

        coVerify(exactly = 0) { azureGraphService.hentEnhet(any(), any()) }
        verify(exactly = 0) { repository.vedlikeholdSaksbehandler(any(), any(), any()) }
    }

    @Test
    fun `vedlikeholder saksbehandler når det har gått mer enn 24 timer`() = runBlocking {
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns
            saksbehandler(sistOppdatert = nå.minusHours(25))
        coEvery { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) } returns "3450"
        every { repository.vedlikeholdSaksbehandler(any(), any(), any()) } returns 1

        tjeneste.hentInnloggetBruker(bruker)

        coVerify(exactly = 1) { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) }
        verify(exactly = 1) { repository.vedlikeholdSaksbehandler(any(), any(), nå) }
    }

    @Test
    fun `forsøker på nytt etter feil fra Azure`() = runBlocking {
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns
            saksbehandler(sistOppdatert = nå.minusDays(2))
        coEvery { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) } throws
            IllegalStateException("Azure er utilgjengelig")

        tjeneste.hentInnloggetBruker(bruker)

        verify(exactly = 0) { repository.vedlikeholdSaksbehandler(any(), any(), any()) }
    }

    @Test
    fun `epostkonflikt avbryter ikke innlogging`() = runBlocking {
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns saksbehandler(null)
        coEvery { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) } returns "3450"
        every { repository.vedlikeholdSaksbehandler(any(), any(), any()) } throws
            PSQLException(ServerErrorMessage("C23505\u0000nsaksbehandler_epost_key\u0000"))

        val resultat = tjeneste.hentInnloggetBruker(bruker)

        assertThat(resultat.finnesISaksbehandlerTabell).isEqualTo(true)
    }

    @Test
    fun `andre databasefeil kastes videre`() = runBlocking {
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns saksbehandler(null)
        coEvery { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) } returns "3450"
        every { repository.vedlikeholdSaksbehandler(any(), any(), any()) } throws
            PSQLException(ServerErrorMessage("C23503\u0000nsaksbehandler_epost_key\u0000"))

        assertThrows<PSQLException> { tjeneste.hentInnloggetBruker(bruker) }
    }

    @Test
    fun `faller tilbake til oppslag på epost`() {
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns null
        every { repository.finnSaksbehandlerMedEpost("saksbehandler@nav.no") } returns saksbehandler(null)

        assertThat(tjeneste.finnSaksbehandler("Z123456", "saksbehandler@nav.no")?.id).isEqualTo(1)
    }

    @Test
    fun `returnerer områdespesifikke tilganger`() = runBlocking {
        val utenK9Tilgang = TestKontekstFactory.brukerkontekst(
            område = Områder.K9,
            tilganger = TestKontekstFactory.INGEN_TILGANGER,
        )
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns
            saksbehandler(sistOppdatert = nå.minusHours(1))

        val resultat = tjeneste.hentInnloggetBruker(utenK9Tilgang)

        assertThat(resultat.harBasisTilgang).isEqualTo(false)
        assertThat(resultat.kanOppgavestyre).isEqualTo(false)
    }

    @Test
    fun `vedlikehold bevarer global kode6-status uavhengig av valgt område`() = runBlocking {
        val globaltKode6 = saksbehandler(null, kode6 = true)
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns globaltKode6
        coEvery { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) } returns "3450"
        every { repository.vedlikeholdSaksbehandler(any(), any(), any()) } returns 1

        tjeneste.hentInnloggetBruker(bruker)

        verify(exactly = 1) {
            repository.vedlikeholdSaksbehandler(
                match { it.kode6 },
                skjermet = true,
                oppdatertTidspunkt = nå,
            )
        }
    }

    @Test
    fun `områdeløst områdeoppslag endrer ikke global kode6-status`() = runBlocking {
        val resultat = tjeneste.hentBrukersOmråder(områdeløsBruker)

        assertThat(resultat).isEqualTo(listOf(Områder.K9, Områder.AKTIVITETSPENGER))
        verify(exactly = 0) { repository.finnSaksbehandlerMedIdent(any<String>()) }
        coVerify(exactly = 0) { azureGraphService.hentEnhet(any(), any()) }
        verify(exactly = 0) { repository.vedlikeholdSaksbehandler(any(), any(), any()) }
    }

    private fun saksbehandler(sistOppdatert: LocalDateTime?, kode6: Boolean = false) = Saksbehandler(
        id = 1,
        navident = "Z123456",
        navn = "Saksbehandler Sara",
        epost = "saksbehandler@nav.no",
        enhet = "3450",
        områder = listOf(Områder.K9, Områder.AKTIVITETSPENGER),
        kode6 = kode6,
        sistOppdatert = sistOppdatert,
    )
}
