package no.nav.k9.los.innloggetbruker

import assertk.assertThat
import assertk.assertions.isEqualTo
import io.mockk.*
import kotlinx.coroutines.CancellationException
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

    @Test
    fun `vedlikeholder tokenfelter og tidspunkt uten aa mutere opprinnelig saksbehandler`() = runBlocking {
        val opprinnelig = saksbehandler(null)
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns opprinnelig
        coEvery { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) } returns "1234"
        every { repository.vedlikeholdSaksbehandler(any()) } returns 1

        val resultat = tjeneste.hentInnloggetBruker(bruker)

        assertThat(resultat.brukerIdent).isEqualTo("Z123456")
        verify(exactly = 1) {
            repository.vedlikeholdSaksbehandler(match {
                it.id == 1L && it.enhet == "1234" && it.navident == bruker.navIdent &&
                    it.navn == bruker.idToken.getName() && it.epost == bruker.idToken.getPreferredUsername() &&
                    it.sistOppdatert == nå && it.områder == opprinnelig.områder
            })
        }
        assertThat(opprinnelig.enhet).isEqualTo("3450")
        assertThat(opprinnelig.sistOppdatert).isEqualTo(null)
    }

    @Test
    fun `vedlikeholder ikke foer eller noeyaktig etter 24 timer`() = runBlocking {
        listOf(23L, 24L).forEach { timer ->
            every { repository.finnSaksbehandlerMedIdent("Z123456") } returns saksbehandler(nå.minusHours(timer))
            tjeneste.hentInnloggetBruker(bruker)
        }

        coVerify(exactly = 0) { azureGraphService.hentEnhet(any(), any()) }
        verify(exactly = 0) { repository.vedlikeholdSaksbehandler(any()) }
    }

    @Test
    fun `vedlikeholder etter mer enn 24 timer`() = runBlocking {
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns saksbehandler(nå.minusHours(25))
        coEvery { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) } returns "3450"
        every { repository.vedlikeholdSaksbehandler(any()) } returns 1

        tjeneste.hentInnloggetBruker(bruker)

        verify(exactly = 1) { repository.vedlikeholdSaksbehandler(match { it.sistOppdatert == nå }) }
    }

    @Test
    fun `forsoeker paa nytt etter feil fra Azure`() = runBlocking {
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns saksbehandler(null)
        coEvery { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) } throws IllegalStateException("Azure er utilgjengelig")

        repeat(2) { tjeneste.hentInnloggetBruker(bruker) }

        coVerify(exactly = 2) { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) }
        verify(exactly = 0) { repository.vedlikeholdSaksbehandler(any()) }
    }

    @Test
    fun `kansellering av Azure-kall kastes videre`() = runBlocking {
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns saksbehandler(null)
        coEvery { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) } throws CancellationException()

        assertThrows<CancellationException> { tjeneste.hentInnloggetBruker(bruker) }
        verify(exactly = 0) { repository.vedlikeholdSaksbehandler(any()) }
    }

    @Test
    fun `epostkonflikt avbryter ikke innlogging og forsoekes igjen`() = runBlocking {
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns saksbehandler(null)
        coEvery { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) } returns "3450"
        every { repository.vedlikeholdSaksbehandler(any()) } throws
            PSQLException(ServerErrorMessage("C23505\u0000nsaksbehandler_epost_key\u0000"))

        repeat(2) {
            assertThat(tjeneste.hentInnloggetBruker(bruker).finnesISaksbehandlerTabell).isEqualTo(true)
        }
        verify(exactly = 2) { repository.vedlikeholdSaksbehandler(match { it.sistOppdatert == nå }) }
    }

    @Test
    fun `andre databasefeil kastes videre`() = runBlocking {
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns saksbehandler(null)
        coEvery { azureGraphService.hentEnhet(bruker.navIdent, bruker.idToken) } returns "3450"
        listOf("23505" to "saksbehandler_id_key", "23503" to "saksbehandler_epost_key", "23505" to null)
            .forEach { (sqlState, constraint) ->
                val feil = PSQLException(ServerErrorMessage("C$sqlState\u0000" + (constraint?.let { "n$it\u0000" } ?: "")))
                every { repository.vedlikeholdSaksbehandler(any()) } throws feil
                assertThat(assertThrows<PSQLException> { tjeneste.hentInnloggetBruker(bruker) }).isEqualTo(feil)
            }
    }

    @Test
    fun `faller tilbake til oppslag paa epost`() {
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns null
        every { repository.finnSaksbehandlerMedEpost("saksbehandler@nav.no") } returns saksbehandler(null)

        assertThat(tjeneste.finnSaksbehandler("Z123456", "saksbehandler@nav.no")?.id).isEqualTo(1L)
    }

    @Test
    fun `returnerer omraadespesifikke tilganger`() = runBlocking {
        val utenTilgang = TestKontekstFactory.brukerkontekst(Områder.K9, tilganger = TestKontekstFactory.INGEN_TILGANGER)
        every { repository.finnSaksbehandlerMedIdent("Z123456") } returns saksbehandler(nå.minusHours(1))

        val resultat = tjeneste.hentInnloggetBruker(utenTilgang)

        assertThat(resultat.harBasisTilgang).isEqualTo(false)
        assertThat(resultat.kanOppgavestyre).isEqualTo(false)
    }

    @Test
    fun `begge API-er oppdaterer global kode6 fra kontekst PDP i begge retninger og alle omraader`() = runBlocking {
        Områder.entries.forEach { område ->
            listOf(false, true).forEach { kode6 ->
                val kontekst = TestKontekstFactory.brukerkontekst(
                    område, tilganger = TestKontekstFactory.ALLE_TILGANGER.copy(harTilgangTilKode6 = kode6)
                )
                val opprinnelig = saksbehandler(null, kode6 = !kode6)
                every { repository.finnSaksbehandlerMedIdent("Z123456") } returns opprinnelig
                coEvery { azureGraphService.hentEnhet(kontekst.navIdent, kontekst.idToken) } returns "3450"
                every { repository.vedlikeholdSaksbehandler(any()) } returns 1

                tjeneste.hentInnloggetBruker(kontekst)
                tjeneste.hentLegacyInnloggetBruker(kontekst)

                verify(exactly = 2) {
                    repository.vedlikeholdSaksbehandler(match {
                        it.skjermet == kode6 && it.områder == opprinnelig.områder && it.sistOppdatert == nå
                    })
                }
                assertThat(opprinnelig.skjermet).isEqualTo(!kode6)
                clearMocks(repository, answers = false)
            }
        }
    }

    @Test
    fun `omraadeloest oppslag vedlikeholder ikke saksbehandler`() = runBlocking {
        val resultat = tjeneste.hentBrukersOmråder(TestKontekstFactory.brukerkontekstUtenOmråde())

        assertThat(resultat).isEqualTo(Områder.entries.toList())
        verify { repository wasNot Called }
        coVerify(exactly = 0) { azureGraphService.hentEnhet(any(), any()) }
    }

    private fun saksbehandler(sistOppdatert: LocalDateTime?, kode6: Boolean = false) = Saksbehandler(
        id = 1,
        navident = "Z123456",
        navn = "Saksbehandler Sara",
        epost = "saksbehandler@nav.no",
        enhet = "3450",
        områder = Områder.entries.toList(),
        skjermet = kode6,
        sistOppdatert = sistOppdatert,
    )
}
