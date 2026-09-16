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
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.ko.db.OppgaveKoRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import org.junit.jupiter.api.Test

class OppgaveKoTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `sjekker at oppgavekø kan opprettes og slettes`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource, OmrådeRepository(dataSource))

        val oppgaveKo = oppgaveKoRepository.leggTil(Områder.K9, skjermet = false, "Testkø")
        assertThat(oppgaveKo.tittel).isEqualTo("Testkø")

        val oppgaveKoFraDb = oppgaveKoRepository.hent(Områder.K9, false, oppgaveKo.id)
        assertThat(oppgaveKoFraDb).isNotNull()

        oppgaveKoRepository.slett(Områder.K9, false, oppgaveKo.id)
        assertFailure {
            oppgaveKoRepository.hent(Områder.K9, false,oppgaveKo.id)
        }
    }

    @Test
    fun `sjekker at oppgavekø kan endres`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource, OmrådeRepository(dataSource))

        val tittel = "Testkø"
        val oppgaveKo = oppgaveKoRepository.leggTil(Områder.K9, skjermet = false, tittel = tittel)
        assertThat(oppgaveKo.tittel).isEqualTo(tittel)

        val beskrivelse = "En god beskrivelse"
        val oppgaveKoFraDb = oppgaveKoRepository.endre(Områder.K9, false, oppgaveKo.copy(beskrivelse = beskrivelse))
        assertThat(oppgaveKoFraDb).isNotNull()
        assertThat(oppgaveKoFraDb.tittel).isEqualTo(tittel)
        assertThat(oppgaveKoFraDb.beskrivelse).isEqualTo(beskrivelse)
    }

    @Test
    fun `sjekker at oppgavekø kan få saksbehandler tilknyttet og fjernet`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource, OmrådeRepository(dataSource))

        val tittel = "Testkø"
        val oppgaveKo = oppgaveKoRepository.leggTil(Områder.K9, skjermet = false, tittel = tittel)
        assertThat(oppgaveKo.tittel).isEqualTo(tittel)

        val saksbehandlerepost = "a@b"
        val saksbehandler = mockLeggTilSaksbehandler(saksbehandlerepost)


        val oppgaveKoFraDb = oppgaveKoRepository.endre(Områder.K9, false, oppgaveKo.copy(saksbehandlere = listOf(saksbehandlerepost), saksbehandlerIds = listOf(saksbehandler.id)))
        assertThat(oppgaveKoFraDb.saksbehandlere).contains(saksbehandlerepost)
        assertThat(oppgaveKoFraDb.saksbehandlere).hasSize(1)

        val saksbehandlerepost2 = "b@c"
        val saksbehandler2 = mockLeggTilSaksbehandler(saksbehandlerepost2)
        val oppgaveKoFraDb2 = oppgaveKoRepository.endre(Områder.K9, false, oppgaveKoFraDb.copy(saksbehandlere = listOf(saksbehandlerepost2), saksbehandlerIds = listOf(saksbehandler2.id)))
        assertThat(oppgaveKoFraDb2.saksbehandlere).contains(saksbehandlerepost2)
        assertThat(oppgaveKoFraDb2.saksbehandlere).hasSize(1)

        oppgaveKoRepository.slett(Områder.K9, false, oppgaveKoFraDb2.id)
    }

    @Test
    fun `oppgavekø skal kunne kopieres`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource, OmrådeRepository(dataSource))

        val tittel = "Testkø"
        val saksbehandlerepost = "a@b"
        val oppgaveKo = oppgaveKoRepository.leggTil(Områder.K9, skjermet = false, tittel = tittel)
        val saksbehandler = mockLeggTilSaksbehandler(saksbehandlerepost)
        val gammelOppgaveko = oppgaveKoRepository.endre(Områder.K9, false, oppgaveKo.copy(saksbehandlere = listOf(saksbehandlerepost), saksbehandlerIds = listOf(saksbehandler.id)))

        val nyTittel = "Ny tittel"
        val nyOppgaveKo = oppgaveKoRepository.kopier(område = Områder.K9, kopierFraOppgaveId = gammelOppgaveko.id, tittel = nyTittel,
            taMedQuery = true,
            taMedSaksbehandlere = true,
            skjermet = false
        )
        assertThat(nyOppgaveKo.saksbehandlere).contains(saksbehandlerepost)
        assertThat(nyOppgaveKo.saksbehandlere).hasSize(1)
        assertThat(nyOppgaveKo.tittel).isEqualTo(nyTittel)
    }

    private fun mockLeggTilSaksbehandler(saksbehandlerepost: String): Saksbehandler {
        val pepClient = mockk<IPepClient>()
        val testSaksbehandlerRepository = TestSaksbehandlerRepository(dataSource, OmrådeRepository(dataSource))
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
                    områder = listOf(Områder.K9)
                )
            )
        }
    }
}
