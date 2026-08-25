package no.nav.k9.los.ko

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.ko.db.OppgaveKoRepository
import org.junit.jupiter.api.Test
import org.koin.test.get
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

class OppgaveKoTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `sjekker at oppgavekø kan opprettes og slettes`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource, get())

        val oppgaveKo = oppgaveKoRepository.leggTil("Testkø", skjermet = false, område = Områder.K9)
        assertThat(oppgaveKo.tittel).isEqualTo("Testkø")

        val oppgaveKoFraDb = oppgaveKoRepository.hent(oppgaveKo.id, false, Områder.K9)
        assertThat(oppgaveKoFraDb).isNotNull()

        oppgaveKoRepository.slett(oppgaveKo.id, Områder.K9)
        assertFailure {
            oppgaveKoRepository.hent(oppgaveKo.id, false, Områder.K9)
        }
    }

    @Test
    fun `sjekker at oppgavekø kan endres`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource, get())

        val tittel = "Testkø"
        val oppgaveKo = oppgaveKoRepository.leggTil(tittel, skjermet = false, område = Områder.K9)
        assertThat(oppgaveKo.tittel).isEqualTo(tittel)

        val beskrivelse = "En god beskrivelse"
        val oppgaveKoFraDb = oppgaveKoRepository.endre(oppgaveKo.copy(beskrivelse = beskrivelse), false, Områder.K9)
        assertThat(oppgaveKoFraDb).isNotNull()
        assertThat(oppgaveKoFraDb.tittel).isEqualTo(tittel)
        assertThat(oppgaveKoFraDb.beskrivelse).isEqualTo(beskrivelse)
    }

    @Test
    fun `sjekker at oppgavekø kan få saksbehandler tilknyttet og fjernet`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource, get())

        val tittel = "Testkø"
        val oppgaveKo = oppgaveKoRepository.leggTil(tittel, skjermet = false, område = Områder.K9)
        assertThat(oppgaveKo.tittel).isEqualTo(tittel)

        val saksbehandlerepost = "a@b"
        val saksbehandlerId = mockLeggTilSaksbehandler(saksbehandlerepost)


        val oppgaveKoFraDb = oppgaveKoRepository.endre(oppgaveKo.copy(saksbehandlere = listOf(saksbehandlerepost), saksbehandlerIds = listOf(saksbehandlerId)), false, Områder.K9)
        assertThat(oppgaveKoFraDb.saksbehandlere).contains(saksbehandlerepost)
        assertThat(oppgaveKoFraDb.saksbehandlere).hasSize(1)

        val saksbehandlerepost2 = "b@c"
        val saksbehandlerId2 = mockLeggTilSaksbehandler(saksbehandlerepost2)
        val oppgaveKoFraDb2 = oppgaveKoRepository.endre(oppgaveKoFraDb.copy(saksbehandlere = listOf(saksbehandlerepost2), saksbehandlerIds = listOf(saksbehandlerId2)), false, Områder.K9)
        assertThat(oppgaveKoFraDb2.saksbehandlere).contains(saksbehandlerepost2)
        assertThat(oppgaveKoFraDb2.saksbehandlere).hasSize(1)

        oppgaveKoRepository.slett(oppgaveKoFraDb2.id, Områder.K9)
    }

    @Test
    fun `oppgavekø skal kunne kopieres`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource, get())

        val tittel = "Testkø"
        val saksbehandlerepost = "a@b"
        val oppgaveKo = oppgaveKoRepository.leggTil(tittel, skjermet = false, område = Områder.K9)
        val saksbehandlerId = mockLeggTilSaksbehandler(saksbehandlerepost)
        val gammelOppgaveko = oppgaveKoRepository.endre(oppgaveKo.copy(saksbehandlere = listOf(saksbehandlerepost), saksbehandlerIds = listOf(saksbehandlerId)), false, Områder.K9)

        val nyTittel = "Ny tittel"
        val nyOppgaveKo = oppgaveKoRepository.kopier(gammelOppgaveko.id, nyTittel,
            taMedQuery = true,
            taMedSaksbehandlere = true,
            skjermet = false,
            område = Områder.K9
        )
        assertThat(nyOppgaveKo.saksbehandlere).contains(saksbehandlerepost)
        assertThat(nyOppgaveKo.saksbehandlere).hasSize(1)
        assertThat(nyOppgaveKo.tittel).isEqualTo(nyTittel)
    }

    @Test
    fun `oppgavekø skal ikke være tilgjengelig fra et annet område`() {
        val oppgaveKoRepository = OppgaveKoRepository(dataSource, get())

        val k9Kø = oppgaveKoRepository.leggTil("K9-kø", skjermet = false, område = Områder.K9)

        // Lesing
        assertFailure { oppgaveKoRepository.hent(k9Kø.id, false, Områder.AKTIVITETSPENGER) }
        assertThat(oppgaveKoRepository.hentListe(Områder.AKTIVITETSPENGER, skjermet = false)).isEmpty()

        // Mutasjoner
        assertFailure { oppgaveKoRepository.endre(k9Kø.copy(tittel = "Kapret"), false, Områder.AKTIVITETSPENGER) }
        assertFailure {
            oppgaveKoRepository.kopier(
                k9Kø.id,
                "Kopi",
                taMedQuery = true,
                taMedSaksbehandlere = true,
                skjermet = false,
                område = Områder.AKTIVITETSPENGER
            )
        }
        assertFailure { oppgaveKoRepository.slett(k9Kø.id, Områder.AKTIVITETSPENGER) }

        // Køen skal være uendret og fortsatt finnes i sitt eget område
        val uendret = oppgaveKoRepository.hent(k9Kø.id, false, Områder.K9)
        assertThat(uendret.tittel).isEqualTo("K9-kø")
    }

    private fun mockLeggTilSaksbehandler(saksbehandlerepost: String): Long {
        val pepClient = mockk<IPepClient>()
        val saksbehandlerRepository = SaksbehandlerRepository(dataSource, transactionalManager = get(), områdeRepository = get())
        return runBlocking {
            saksbehandlerRepository.addSaksbehandler(saksbehandlerepost, Områder.K9)
        }
    }
}
