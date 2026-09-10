package no.nav.k9.los.lagretsok

import io.mockk.*
import kotliquery.TransactionalSession
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime

class LagretSøkTilgangTest {
    private val repository = mockk<LagretSøkRepository>()
    private val saksbehandlere = mockk<SaksbehandlerRepository>()
    private val query = mockk<OppgaveQueryService>()
    private val tjeneste = LagretSøkTjeneste(saksbehandlere, repository, query, mockk())
    private val bruker = TestKontekstFactory.brukerkontekst(Områder.AKTIVITETSPENGER)
    private val eier = Saksbehandler(1, bruker.navIdent, "Test", "test@nav.no", null,
        listOf(Områder.AKTIVITETSPENGER), false)

    @Test
    fun `hent og antall avviser annet område og annen eier før query`() {
        every { saksbehandlere.finnSaksbehandlerMedIdent(bruker.navIdent, false) } returns eier
        for ((område, eierId) in listOf(Områder.K9 to 1L, Områder.AKTIVITETSPENGER to 2L)) {
            every { repository.hent(10) } returns LagretSøk.fraEksisterende(10, eierId, område,
                1, "Søk", "", LocalDateTime.now())
            assertThrows<IllegalArgumentException> { tjeneste.hent(bruker, 10) }
            assertThrows<IllegalArgumentException> { tjeneste.hentAntall(bruker, 10) }
        }
        verify { query wasNot Called }
    }

    @Test
    fun `antall bruker søkets område og serverstyrt kode6`() {
        every { saksbehandlere.finnSaksbehandlerMedIdent(bruker.navIdent, false) } returns eier
        every { repository.hent(10) } returns LagretSøk.fraEksisterende(10, 1, bruker.område,
            1, "Søk", "", LocalDateTime.now())
        every { query.queryForAntall(any(), any()) } returns 3

        assertEquals(3L, tjeneste.hentAntall(bruker, 10))
        verify { query.queryForAntall(match { it.område == Områder.AKTIVITETSPENGER && it.harTilgangTilKode6 == false }, any()) }
    }

    @Test
    fun `endre og slett avviser direkte id i annet område`() {
        every { saksbehandlere.finnSaksbehandlerMedIdent(bruker.navIdent, false) } returns eier
        val søk = LagretSøk.fraEksisterende(10, eier.id, Områder.K9, 1, "Søk", "", LocalDateTime.now())
        every { repository.hent(10) } returns søk

        assertThrows<IllegalArgumentException> {
            tjeneste.endre(bruker, EndreLagretSøkRequest(id = 10, versjon = 1, tittel = "Endret", beskrivelse = "", query = søk.query))
        }
        assertThrows<IllegalArgumentException> { tjeneste.slett(bruker, 10) }
        verify(exactly = 0) {
            repository.endre(any())
            with(any<TransactionalSession>()) { repository.slett(any()) }
        }
    }

    @Test
    fun `delt kopiering bevares innen området men avvises på tvers`() {
        every { repository.opprett(any()) } returns 20
        every { repository.hent(10) } returns LagretSøk.fraEksisterende(10, 2, bruker.område,
            1, "Delt", "", LocalDateTime.now())
        assertEquals(20L, tjeneste.kopier(bruker, 10, "Kopi", eier))
        verify { repository.opprett(match { it.lagetAv == eier.id && it.område == bruker.område }) }

        every { repository.hent(10) } returns LagretSøk.fraEksisterende(10, 2, Områder.K9,
            1, "Delt", "", LocalDateTime.now())
        assertThrows<IllegalArgumentException> { tjeneste.kopier(bruker, 10, "Kopi", eier) }
        verify(exactly = 1) { repository.opprett(any()) }
    }
}
