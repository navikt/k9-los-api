package no.nav.k9.los.saksbehandleradmin

import io.mockk.*
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.ko.db.OppgaveKoRepository
import no.nav.k9.los.lagretsok.LagretSøkTjeneste
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.reservasjon.ManglerTilgangException
import no.nav.k9.los.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.uttrekk.UttrekkTjeneste
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals

class SaksbehandlerAdminTjenesteTest {
    private val repository = mockk<SaksbehandlerRepository>()
    private val transaksjoner = mockk<TransactionalManager>()
    private val køer = mockk<OppgaveKoRepository>()
    private val søk = mockk<LagretSøkTjeneste>()
    private val uttrekk = mockk<UttrekkTjeneste>()
    private val reservasjoner = mockk<ReservasjonV3Tjeneste>()
    private val tjeneste = SaksbehandlerAdminTjeneste(transaksjoner, repository, køer, søk, uttrekk, reservasjoner)

    @Test
    fun `registrering legger bare til valgt omraade og bevarer globale data`() {
        val saksbehandler = saksbehandler(listOf(Områder.K9))
        every { repository.finnSaksbehandlerMedEpost(saksbehandler.epost) } returns saksbehandler
        every { repository.leggTilOmråde(saksbehandler.id, Områder.AKTIVITETSPENGER) } just Runs

        tjeneste.leggTilSaksbehandlerForEpost(saksbehandler.epost, Områder.AKTIVITETSPENGER, false)

        verify(exactly = 1) { repository.leggTilOmråde(saksbehandler.id, Områder.AKTIVITETSPENGER) }
        verify(exactly = 0) { repository.vedlikeholdSaksbehandler(any()) }
        assertEquals(listOf(Områder.K9), saksbehandler.områder)
    }

    @Test
    fun `registrering avviser annen skjermingskategori`() {
        val saksbehandler = saksbehandler(listOf(Områder.K9), kode6 = true)
        every { repository.finnSaksbehandlerMedEpost(saksbehandler.epost) } returns saksbehandler

        assertThrows<IllegalStateException> {
            tjeneste.leggTilSaksbehandlerForEpost(saksbehandler.epost, Områder.AKTIVITETSPENGER, false)
        }
        verify(exactly = 0) { repository.leggTilOmråde(any(), any()) }
    }

    @Test
    fun `sletting med id eller epost i annet omraade avvises uten aa endre data`() {
        val bruker = TestKontekstFactory.brukerkontekst(Områder.K9)
        listOf(listOf(Områder.AKTIVITETSPENGER)).forEach { områder ->
            val saksbehandler = saksbehandler(områder)
            every { repository.finnSaksbehandlerMedId(1) } returns saksbehandler
            every { repository.finnSaksbehandlerMedEpost(saksbehandler.epost, false) } returns saksbehandler

            assertThrows<ManglerTilgangException> { tjeneste.slettSaksbehandlerForId(1, bruker) }
            assertThrows<ManglerTilgangException> { tjeneste.slettSaksbehandler(saksbehandler.epost, bruker) }
        }

        verify { listOf(transaksjoner, køer, søk, uttrekk, reservasjoner) wasNot Called }
        verify(exactly = 0) { repository.slettSaksbehandlerForId(any(), any(), any()) }
        verify(exactly = 0) { repository.slettSaksbehandler(any(), any(), any()) }
        verify(exactly = 0) { repository.fjernOmrådeFraSaksbehandler(any(), any(), any(), any()) }
    }

    @Test
    fun `sletting avviser feil kode6 eller manglende oppgavestyring`() {
        val saksbehandler = saksbehandler(listOf(Områder.K9), kode6 = true)
        every { repository.finnSaksbehandlerMedId(1) } returns saksbehandler
        assertThrows<ManglerTilgangException> {
            tjeneste.slettSaksbehandlerForId(1, TestKontekstFactory.brukerkontekst(Områder.K9))
        }
        val utenRett = TestKontekstFactory.brukerkontekst(
            Områder.K9, tilganger = TestKontekstFactory.ALLE_TILGANGER.copy(
                harTilgangTilKode6 = true, erOppgavestyrer = false
            )
        )
        every { repository.finnSaksbehandlerMedEpost(saksbehandler.epost, true) } returns saksbehandler
        assertThrows<ManglerTilgangException> { tjeneste.slettSaksbehandler(saksbehandler.epost, utenRett) }
        verify { listOf(transaksjoner, køer, søk, uttrekk, reservasjoner) wasNot Called }
    }

    private fun saksbehandler(områder: List<Områder>, kode6: Boolean = false) =
        Saksbehandler(1, "Z123456", "Test", "test@nav.no", "3450", områder, kode6)
}
