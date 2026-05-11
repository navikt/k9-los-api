package no.nav.k9.los.nyoppgavestyring.ko

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
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.ko.db.OppgaveKoRepository
import org.junit.jupiter.api.Test
import org.koin.test.get

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
        val saksbehandlerId = mockLeggTilSaksbehandler(saksbehandlerepost)


        val oppgaveKoFraDb = oppgaveKoRepository.endre(oppgaveKo.copy(saksbehandlere = listOf(saksbehandlerepost), saksbehandlerIds = listOf(saksbehandlerId)), false)
        assertThat(oppgaveKoFraDb.saksbehandlere).contains(saksbehandlerepost)
        assertThat(oppgaveKoFraDb.saksbehandlere).hasSize(1)

        val saksbehandlerepost2 = "b@c"
        val saksbehandlerId2 = mockLeggTilSaksbehandler(saksbehandlerepost2)
        val oppgaveKoFraDb2 = oppgaveKoRepository.endre(oppgaveKoFraDb.copy(saksbehandlere = listOf(saksbehandlerepost2), saksbehandlerIds = listOf(saksbehandlerId2)), false)
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
        val saksbehandlerId = mockLeggTilSaksbehandler(saksbehandlerepost)
        val gammelOppgaveko = oppgaveKoRepository.endre(oppgaveKo.copy(saksbehandlere = listOf(saksbehandlerepost), saksbehandlerIds = listOf(saksbehandlerId)), false)

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

    private fun mockLeggTilSaksbehandler(saksbehandlerepost: String): Long {
        val pepClient = mockk<IPepClient>()
        val saksbehandlerRepository = SaksbehandlerRepository(dataSource, pepClient, transactionalManager = get())
        coEvery {
            pepClient.harTilgangTilKode6()
        } returns true

        return runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    navident = "Ident$saksbehandlerepost",
                    navn = "Navn for $saksbehandlerepost",
                    epost = saksbehandlerepost,
                    enhet = null
                )
            )
        }
    }
}