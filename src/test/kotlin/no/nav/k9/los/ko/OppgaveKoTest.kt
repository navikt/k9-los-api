package no.nav.k9.los.ko

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.ko.db.OppgaveKoRepository
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime

class OppgaveKoTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `sjekker at oppgavekø kan opprettes og slettes`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource)

        val oppgaveKo = oppgaveKoRepository.leggTil("Testkø", skjermet = false)
        assertThat(oppgaveKo.tittel).isEqualTo("Testkø")

        val oppgaveKoFraDb = oppgaveKoRepository.hent(oppgaveKo.id, false)
        assertThat(oppgaveKoFraDb).isNotNull()

        oppgaveKoRepository.slett(oppgaveKo.id)
        assertFailure {
            oppgaveKoRepository.hent(oppgaveKo.id, false)
        }
    }

    @Test
    fun `sjekker at oppgavekø kan endres`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource)

        val tittel = "Testkø"
        val oppgaveKo = oppgaveKoRepository.leggTil(tittel, skjermet = false)
        assertThat(oppgaveKo.tittel).isEqualTo(tittel)

        val beskrivelse = "En god beskrivelse"
        val oppgaveKoFraDb = oppgaveKoRepository.endre(oppgaveKo.copy(beskrivelse = beskrivelse), false)
        assertThat(oppgaveKoFraDb).isNotNull()
        assertThat(oppgaveKoFraDb.tittel).isEqualTo(tittel)
        assertThat(oppgaveKoFraDb.beskrivelse).isEqualTo(beskrivelse)
    }

    @Test
    fun `sjekker at oppgavekø kan få saksbehandler tilknyttet og fjernet`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource)

        val tittel = "Testkø"
        val oppgaveKo = oppgaveKoRepository.leggTil(tittel, skjermet = false)
        assertThat(oppgaveKo.tittel).isEqualTo(tittel)

        val saksbehandlerepost = "a@b"
        val saksbehandler = mockLeggTilSaksbehandler(saksbehandlerepost)


        val oppgaveKoFraDb = oppgaveKoRepository.endre(oppgaveKo.copy(saksbehandlere = listOf(saksbehandlerepost), saksbehandlerIds = listOf(saksbehandler.id)), false)
        assertThat(oppgaveKoFraDb.saksbehandlere).contains(saksbehandlerepost)
        assertThat(oppgaveKoFraDb.saksbehandlere).hasSize(1)

        val saksbehandlerepost2 = "b@c"
        val saksbehandler2 = mockLeggTilSaksbehandler(saksbehandlerepost2)
        val oppgaveKoFraDb2 = oppgaveKoRepository.endre(oppgaveKoFraDb.copy(saksbehandlere = listOf(saksbehandlerepost2), saksbehandlerIds = listOf(saksbehandler2.id)), false)
        assertThat(oppgaveKoFraDb2.saksbehandlere).contains(saksbehandlerepost2)
        assertThat(oppgaveKoFraDb2.saksbehandlere).hasSize(1)

        oppgaveKoRepository.slett(oppgaveKoFraDb2.id)
    }

    @Test
    fun `oppgavekø skal kunne kopieres`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource)

        val tittel = "Testkø"
        val saksbehandlerepost = "a@b"
        val oppgaveKo = oppgaveKoRepository.leggTil(tittel, skjermet = false)
        val saksbehandler = mockLeggTilSaksbehandler(saksbehandlerepost)
        val gammelOppgaveko = oppgaveKoRepository.endre(oppgaveKo.copy(saksbehandlere = listOf(saksbehandlerepost), saksbehandlerIds = listOf(saksbehandler.id)), false)

        val nyTittel = "Ny tittel"
        val nyOppgaveKo = oppgaveKoRepository.kopier(gammelOppgaveko.id, nyTittel,
            taMedQuery = true,
            taMedSaksbehandlere = true,
            skjermet = false
        )
        assertThat(nyOppgaveKo.saksbehandlere).contains(saksbehandlerepost)
        assertThat(nyOppgaveKo.saksbehandlere).hasSize(1)
        assertThat(nyOppgaveKo.tittel).isEqualTo(nyTittel)
    }

    @Test
    fun `epostbytte bevarer køtilknytning og bruker ny epost ved oppslag og kopiering`() = runBlocking {
        val repository = OppgaveKoRepository(dataSource)
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        val saksbehandler = mockLeggTilSaksbehandler("gammel@nav.no")
        val ko = repository.leggTil("Testkø", skjermet = false)
        repository.endre(ko.copy(saksbehandlerIds = listOf(saksbehandler.id)), false)

        saksbehandlerRepository.vedlikeholdSaksbehandler(
            Saksbehandler(saksbehandler.id, saksbehandler.navident, saksbehandler.navn, "ny@nav.no", saksbehandler.enhet),
            LocalDateTime.parse("2026-08-28T10:00:00")
        )

        val oppdatertKo = repository.hent(ko.id, false)
        assertThat(oppdatertKo.saksbehandlerIds).isEqualTo(listOf(saksbehandler.id))
        assertThat(oppdatertKo.saksbehandlere).isEqualTo(listOf("ny@nav.no"))
        val koer = get<TransactionalManager>().transaction { tx ->
            repository.hentKoerMedOppgittSaksbehandler(tx, saksbehandler.id, false, true)
        }
        assertThat(koer.map { it.id }).isEqualTo(listOf(ko.id))
        assertThat(koer.single().saksbehandlere).isEqualTo(listOf("ny@nav.no"))

        val kopi = repository.kopier(ko.id, "Kopi", taMedQuery = true, taMedSaksbehandlere = true, skjermet = false)
        assertThat(kopi.saksbehandlerIds).isEqualTo(listOf(saksbehandler.id))
        assertThat(kopi.saksbehandlere).isEqualTo(listOf("ny@nav.no"))
    }

    @Test
    fun `ukjent saksbehandler ignoreres ved lagring av kotilknytning`() {
        val repository = OppgaveKoRepository(dataSource)
        val saksbehandler = mockLeggTilSaksbehandler("kjent@nav.no")
        val ko = repository.leggTil("Testkø", skjermet = false)

        val lagret = repository.endre(ko.copy(saksbehandlerIds = listOf(saksbehandler.id, Long.MAX_VALUE)), false)

        assertThat(lagret.saksbehandlerIds).isEqualTo(listOf(saksbehandler.id))
        assertThat(lagret.saksbehandlere).isEqualTo(listOf(saksbehandler.epost))
    }

    private fun mockLeggTilSaksbehandler(saksbehandlerepost: String): Saksbehandler {
        val pepClient = mockk<IPepClient>()
        val testSaksbehandlerRepository = TestSaksbehandlerRepository(dataSource, pepClient)
        coEvery {
            pepClient.harTilgangTilKode6()
        } returns true

        return runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = "Ident$saksbehandlerepost",
                    navn = "Navn for $saksbehandlerepost",
                    epost = saksbehandlerepost,
                    enhet = null,
                )
            )
        }
    }
}
