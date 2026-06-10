package no.nav.k9.los.domeneadaptere.k9.eventmottak

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import io.mockk.every
import io.mockk.mockk
import kotliquery.TransactionalSession
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.kodeverk.Fagsystem
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OutOfOrderMessageCheckerTest {

    private val eventRepository = mockk<EventRepository>()
    private val checker = OutOfOrderMessageChecker(eventRepository)
    private val tx = mockk<TransactionalSession>()
    private val eventnokkel = EventNøkkel(Fagsystem.PUNSJ, "ekstern-id", 1L)

    @Test
    fun `returns false when there are no events`() {
        every { eventRepository.hentAlleEventerMedLås(eventnokkel, tx) } returns emptyList()

        val outOfOrder = checker.checkOutOfOrder(eventnokkel, tx)

        assertThat(outOfOrder).isFalse()
    }

    @Test
    fun `returns true when an older dirty event exists behind a newer clean event`() {
        every { eventRepository.hentAlleEventerMedLås(eventnokkel, tx) } returns listOf(
            punsjEvent(eksternVersjon = "2026-06-10T10:00:00", dirty = true),
            punsjEvent(eksternVersjon = "2026-06-10T11:00:00", dirty = false),
        )

        val outOfOrder = checker.checkOutOfOrder(eventnokkel, tx)

        assertThat(outOfOrder).isTrue()
    }

    @Test
    fun `returns false when dirty events are newer than clean events`() {
        every { eventRepository.hentAlleEventerMedLås(eventnokkel, tx) } returns listOf(
            punsjEvent(eksternVersjon = "2026-06-10T10:00:00", dirty = false),
            punsjEvent(eksternVersjon = "2026-06-10T11:00:00", dirty = true),
        )

        val outOfOrder = checker.checkOutOfOrder(eventnokkel, tx)

        assertThat(outOfOrder).isFalse()
    }

    private fun punsjEvent(eksternVersjon: String, dirty: Boolean): EventLagret {
        return EventLagret.K9Punsj(
            nøkkelId = 1L,
            eksternId = eventnokkel.eksternId,
            eksternVersjon = eksternVersjon,
            eventJson = "{}",
            opprettet = LocalDateTime.parse(eksternVersjon),
            dirty = dirty,
        )
    }
}
