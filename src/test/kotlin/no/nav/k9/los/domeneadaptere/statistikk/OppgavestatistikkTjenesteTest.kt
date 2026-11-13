package no.nav.k9.los.domeneadaptere.statistikk

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotliquery.TransactionalSession
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.kodeverk.AvpOppgavetypenavn
import no.nav.k9.los.domeneadaptere.statistikk.avp.AvpStatistikkPublisher
import no.nav.k9.los.infrastruktur.abac.cache.PepCacheRepository
import no.nav.k9.los.infrastruktur.db.Appversjonsvakt
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import org.junit.jupiter.api.Test

class OppgavestatistikkTjenesteTest {
    private val avpPublisher = mockk<AvpStatistikkPublisher>(relaxUnitFun = true)
    private val transactionalManager = mockk<TransactionalManager>()
    private val statistikkRepository = mockk<StatistikkRepository>(relaxUnitFun = true)
    private val pepCacheRepository = mockk<PepCacheRepository>()
    private val tx = mockk<TransactionalSession>()
    private val oppgave = mockk<Oppgave> {
        every { område } returns Områder.AKTIVITETSPENGER
        every { eksternId } returns "behandling-1"
        every { hentVerdi("saksnummer") } returns "sak-1"
    }
    private val appversjonsvakt = mockk<Appversjonsvakt> { every { erAktiv(any()) } returns true }
    private val tjeneste = OppgavestatistikkTjeneste(
        mockk(relaxUnitFun = true),
        avpPublisher,
        mockk(relaxUnitFun = true),
        transactionalManager,
        statistikkRepository,
        pepCacheRepository,
        appversjonsvakt,
    )

    @Test
    fun `kode6 AVP kvitteres uten at noen melding publiseres`() {
        klargjør(kode6 = true)

        tjeneste.spillAvUsendtStatistikk()

        verify(exactly = 0) { avpPublisher.publiserAsynkront(any()) }
        verify(exactly = 1) { statistikkRepository.kvitterSending(tx, 1L) }
    }

    @Test
    fun `AVP uten kode6 publiseres uten sensur og kvitteres`() {
        klargjør(kode6 = false)
        every { oppgave.hentVerdi(any()) } returns null
        every { oppgave.hentVerdi("saksnummer") } returns "sak-1"
        every { oppgave.hentVerdi("aktorId") } returns "123456789"
        every { oppgave.hentVerdi("behandlendeEnhet") } returns "1234"
        every { oppgave.eksternVersjon } returns "2026-01-01T12:00:00"
        every { oppgave.oppgavetype.eksternId } returns AvpOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1.kode

        tjeneste.spillAvUsendtStatistikk()

        verify(exactly = 1) {
            avpPublisher.publiserAsynkront(match {
                it.behandlingId == "behandling-1" &&
                    it.saksnummer == "sak-1" &&
                    it.søkersAktørId.aktorId == 123456789L &&
                    it.ansvarligEnhetKode == "1234"
            })
        }
        verify(exactly = 1) { statistikkRepository.kvitterSending(tx, 1L) }
    }

    @Test
    fun `sender og kvitterer ingenting når appversjonen ikke er aktiv`() {
        klargjør(kode6 = false)
        every { appversjonsvakt.erAktiv(tx) } returns false

        tjeneste.spillAvUsendtStatistikk()

        verify(exactly = 0) { statistikkRepository.hentOppgaveForId(any(), any(), any()) }
        verify(exactly = 0) { avpPublisher.publiserAsynkront(any()) }
        verify(exactly = 0) { avpPublisher.flushOgValider() }
        verify(exactly = 0) { statistikkRepository.kvitterSending(any(), any()) }
    }

    private fun klargjør(kode6: Boolean) {
        every { statistikkRepository.hentOppgaverSomIkkeErSendt(any()) } returns emptyList()
        every { statistikkRepository.hentOppgaverSomIkkeErSendt(Områder.AKTIVITETSPENGER) } returns listOf(1L)
        every { statistikkRepository.hentOppgaveForId(tx, 1L, any()) } returns (oppgave to 1)
        every { transactionalManager.transaction<Boolean>(any()) } answers {
            firstArg<(TransactionalSession) -> Boolean>().invoke(tx)
        }
        every { pepCacheRepository.hent(Områder.AKTIVITETSPENGER, "behandling-1", tx) } returns mockk {
            every { this@mockk.kode6 } returns kode6
        }
    }
}
