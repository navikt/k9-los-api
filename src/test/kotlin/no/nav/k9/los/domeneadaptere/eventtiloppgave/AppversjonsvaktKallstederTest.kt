package no.nav.k9.los.domeneadaptere.eventtiloppgave

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotliquery.TransactionalSession
import no.nav.k9.los.domeneadaptere.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventlager.HistorikkvaskBestilling
import no.nav.k9.los.infrastruktur.db.Appversjonsvakt
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavemottak.OppgaveV3Tjeneste
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class AppversjonsvaktKallstederTest {
    private val tx = mockk<TransactionalSession>()
    private val eventRepository = mockk<EventRepository>()
    private val oppgaveV3Tjeneste = mockk<OppgaveV3Tjeneste>()
    private val transactionalManager = mockk<TransactionalManager> {
        every { transaction<Unit>(any()) } answers { firstArg<(TransactionalSession) -> Unit>().invoke(tx) }
    }
    private val inaktivVakt = mockk<Appversjonsvakt> { every { erAktiv(tx) } returns false }

    private val adapter = EventTilOppgaveAdapter(
        eventRepository = eventRepository,
        oppgaveV3Tjeneste = oppgaveV3Tjeneste,
        transactionalManager = transactionalManager,
        eventBeriker = mockk(),
        oppgaveOppdatertHandler = mockk(),
        ajourholdTjeneste = mockk(),
        statistikkRepository = mockk(),
        feilRekkefølgeSjekker = mockk(),
        appversjonsvakt = inaktivVakt,
    )

    @Test
    fun `adapter rører ikke eventer når appversjonen ikke er aktiv`() {
        val teller = adapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.K9SAK, "ekstern-1"), tx, 7)

        assertEquals(7, teller)
        verify(exactly = 0) { eventRepository.hentAlleEventerMedLås(any(), any()) }
    }

    @Test
    fun `historikkvask rører ikke bestillingen når appversjonen ikke er aktiv`() {
        val tjeneste = HistorikkvaskTjeneste(
            eventRepository = eventRepository,
            oppgaveV3Tjeneste = oppgaveV3Tjeneste,
            transactionalManager = transactionalManager,
            eventTilOppgaveAdapter = adapter,
            appversjonsvakt = inaktivVakt,
        )

        val antall = tjeneste.vaskBestilling(HistorikkvaskBestilling(1L, "ekstern-1", Fagsystem.K9SAK))

        assertEquals(0, antall)
        verify(exactly = 0) { eventRepository.hentOgLåsEventnøkkel(any(), any(), any()) }
        verify(exactly = 0) { eventRepository.settDirty(any<EventNøkkel>(), any()) }
        verify(exactly = 0) { eventRepository.settHistorikkvaskFerdig(any<Long>(), any()) }
    }
}
