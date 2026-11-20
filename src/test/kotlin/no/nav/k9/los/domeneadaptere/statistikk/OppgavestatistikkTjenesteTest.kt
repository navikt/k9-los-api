package no.nav.k9.los.domeneadaptere.statistikk

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import kotliquery.TransactionalSession
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.kodeverk.AvpOppgavetypenavn
import no.nav.k9.los.domeneadaptere.statistikk.avp.AvpBehandling
import no.nav.k9.los.infrastruktur.abac.cache.PepCacheRepository
import no.nav.k9.los.infrastruktur.db.Appversjonsvakt
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.Oppgave
import org.junit.jupiter.api.Test

class OppgavestatistikkTjenesteTest {
    private val avpPublisher = mockk<StatistikkPublisher>(relaxUnitFun = true)
    private val transactionalManager = mockk<TransactionalManager>()
    private val statistikkRepository = mockk<StatistikkRepository>(relaxUnitFun = true)
    private val pepCacheRepository = mockk<PepCacheRepository>()
    private val tx = mockk<TransactionalSession>()
    private val eventRepository = mockk<EventRepository> {
        every { hentOgLåsEventnøkkel(any(), any(), tx) } returns 1L
    }
    private val oppgaveversjon = DvhOppgaveversjon(
        AvpOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1, "behandling-1", "2026-01-01T12:00:00"
    )
    private val oppgave = mockk<Oppgave> {
        every { område } returns Områder.AKTIVITETSPENGER
        every { eksternId } returns "behandling-1"
        every { hentVerdi("saksnummer") } returns "sak-1"
    }
    private val appversjonsvakt = mockk<Appversjonsvakt> { every { erAktiv(any()) } returns true }
    private val tjeneste = OppgavestatistikkTjeneste(
        StatistikkPublishere(
            k9StatistikkSak = mockk(relaxUnitFun = true),
            k9StatistikkBehandling = mockk(relaxUnitFun = true),
            avpStatistikkBehandling = avpPublisher,
            ungStatistikkBehandling = mockk(relaxUnitFun = true),
        ),
        transactionalManager,
        statistikkRepository,
        pepCacheRepository,
        appversjonsvakt,
        eventRepository,
    )

    @Test
    fun `kode6 AVP kvitteres uten at noen melding publiseres`() {
        klargjør(kode6 = true)

        tjeneste.spillAvUsendtStatistikk()

        verify(exactly = 0) { avpPublisher.publiserAsynkront(any(), any()) }
        verify(exactly = 1) { statistikkRepository.kvitterSending(tx, oppgaveversjon) }
    }

    @Test
    fun `henter usendte oppgaver for hver konfigurert oppgavetype`() {
        klargjør(kode6 = true)

        tjeneste.spillAvUsendtStatistikk()

        StatistikkRepository.ALLE_DVH_OPPGAVETYPER.values.flatten().forEach { oppgavetype ->
            verify(exactly = 1) { statistikkRepository.hentOppgaverSomIkkeErSendt(oppgavetype) }
        }
    }

    @Test
    fun `sender og kvitterer begge AVP oppgavetyper hver for seg`() {
        klargjør(kode6 = true)
        val del2 = oppgaveversjon.copy(oppgavetype = AvpOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2)
        every {
            statistikkRepository.hentOppgaverSomIkkeErSendt(
                AvpOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2
            )
        } returns listOf(del2)
        every { statistikkRepository.hentOppgaveForSending(tx, del2, any()) } returns (oppgave to 1)

        tjeneste.spillAvUsendtStatistikk()

        verify(exactly = 1) { statistikkRepository.kvitterSending(tx, oppgaveversjon) }
        verify(exactly = 1) { statistikkRepository.kvitterSending(tx, del2) }
        verify(exactly = 2) { avpPublisher.flushOgValider() }
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
            avpPublisher.publiserAsynkront("behandling-1", match<AvpBehandling> {
                it.behandlingId == "behandling-1" &&
                    it.saksnummer == "sak-1" &&
                    it.søkersAktørId.aktorId == 123456789L &&
                    it.ansvarligEnhetKode == "1234"
            })
        }
        verify(exactly = 1) { statistikkRepository.kvitterSending(tx, oppgaveversjon) }
        verifyOrder {
            appversjonsvakt.erAktiv(tx)
            eventRepository.hentOgLåsEventnøkkel(oppgaveversjon.oppgavetype.fagsystem, oppgaveversjon.eksternId, tx)
            statistikkRepository.hentOppgaveForSending(tx, oppgaveversjon, any())
            avpPublisher.publiserAsynkront(any(), any())
            avpPublisher.flushOgValider()
            statistikkRepository.kvitterSending(tx, oppgaveversjon)
        }
    }

    @Test
    fun `sender og kvitterer ingenting når appversjonen ikke er aktiv`() {
        klargjør(kode6 = false)
        every { appversjonsvakt.erAktiv(tx) } returns false

        tjeneste.spillAvUsendtStatistikk()

        verify(exactly = 0) { eventRepository.hentOgLåsEventnøkkel(any(), any(), any()) }
        verify(exactly = 0) { statistikkRepository.hentOppgaveForSending(any(), any(), any()) }
        verify(exactly = 0) { avpPublisher.publiserAsynkront(any(), any()) }
        verify(exactly = 0) { avpPublisher.flushOgValider() }
        verify(exactly = 0) { statistikkRepository.kvitterSending(any(), any()) }
    }

    @Test
    fun `oppgave med bestilt vask etter uttrekk hoppes over uten kvittering`() {
        klargjør(kode6 = false)
        every { statistikkRepository.hentOppgaveForSending(tx, oppgaveversjon, any()) } returns null

        tjeneste.spillAvUsendtStatistikk()

        verifyOrder {
            appversjonsvakt.erAktiv(tx)
            eventRepository.hentOgLåsEventnøkkel(oppgaveversjon.oppgavetype.fagsystem, oppgaveversjon.eksternId, tx)
            statistikkRepository.hentOppgaveForSending(tx, oppgaveversjon, any())
        }
        verify(exactly = 0) { pepCacheRepository.hent(any(), any(), any()) }
        verify(exactly = 0) { avpPublisher.publiserAsynkront(any(), any()) }
        verify(exactly = 0) { avpPublisher.flushOgValider() }
        verify(exactly = 0) { statistikkRepository.kvitterSending(any(), any()) }
    }

    @Test
    fun `vask i senere bolk hindrer ikke sending av andre oppgaveversjoner`() {
        klargjør(kode6 = true)
        val oppgaveversjoner = (1..102).map { oppgaveversjon.copy(eksternId = "behandling-$it") }
        every { statistikkRepository.hentOppgaverSomIkkeErSendt(oppgaveversjon.oppgavetype) } returns oppgaveversjoner
        every { statistikkRepository.hentOppgaveForSending(tx, any(), any()) } returns (oppgave to 1)
        every { statistikkRepository.hentOppgaveForSending(tx, oppgaveversjoner[100], any()) } returns null

        tjeneste.spillAvUsendtStatistikk()

        verify(exactly = 2) { appversjonsvakt.erAktiv(tx) }
        verify(exactly = 102) { eventRepository.hentOgLåsEventnøkkel(any(), any(), tx) }
        verify(exactly = 0) { statistikkRepository.kvitterSending(tx, oppgaveversjoner[100]) }
        verify(exactly = 1) { statistikkRepository.kvitterSending(tx, oppgaveversjoner[101]) }
        verify(exactly = 101) { statistikkRepository.kvitterSending(tx, any()) }
        verify(exactly = 2) { avpPublisher.flushOgValider() }
    }

    @Test
    fun `områder som er skrudd av hentes ikke og sendes ikke`() {
        klargjør(kode6 = false)
        every { statistikkRepository.aktiveDvhOppgavetyper } returns
            StatistikkRepository.ALLE_DVH_OPPGAVETYPER.filterKeys { it == Områder.K9 }

        tjeneste.spillAvUsendtStatistikk()

        verify(exactly = 0) { statistikkRepository.hentOppgaverSomIkkeErSendt(AvpOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1) }
        verify(exactly = 0) { avpPublisher.publiserAsynkront(any(), any()) }
        verify(exactly = 0) { avpPublisher.flushOgValider() }
    }

    private fun klargjør(kode6: Boolean) {
        every { statistikkRepository.aktiveDvhOppgavetyper } returns StatistikkRepository.ALLE_DVH_OPPGAVETYPER
        every { statistikkRepository.hentOppgaverSomIkkeErSendt(any()) } returns emptyList()
        every {
            statistikkRepository.hentOppgaverSomIkkeErSendt(
                AvpOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1
            )
        } returns listOf(oppgaveversjon)
        every { statistikkRepository.hentOppgaveForSending(tx, oppgaveversjon, any()) } returns (oppgave to 1)
        every { transactionalManager.transaction<List<DvhOppgaveversjon>?>(any()) } answers {
            firstArg<(TransactionalSession) -> List<DvhOppgaveversjon>?>().invoke(tx)
        }
        every { pepCacheRepository.hent(Områder.AKTIVITETSPENGER, "behandling-1", tx) } returns mockk {
            every { this@mockk.kode6 } returns kode6
        }
    }
}
