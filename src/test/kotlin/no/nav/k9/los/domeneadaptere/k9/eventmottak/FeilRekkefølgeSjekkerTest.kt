package no.nav.k9.los.domeneadaptere.k9.eventmottak

import assertk.assertThat
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class FeilRekkefølgeSjekkerTest {

    private val checker = FeilRekkefølgeSjekker()

    @Test
    fun `returnerer false når det ikke finnes eventer`() {
        val feilRekkefolge = checker.sjekkFeilRekkefølge(emptyList())

        assertThat(feilRekkefolge).isFalse()
    }

    @Test
    fun `returnerer true når eldre dirty event ligger bak nyere clean event`() {
        val feilRekkefolge = checker.sjekkFeilRekkefølge(listOf(
            punsjEvent(eksternVersjon = "2026-06-10T10:00:00", dirty = true),
            punsjEvent(eksternVersjon = "2026-06-10T11:00:00", dirty = false),
        ))

        assertThat(feilRekkefolge).isTrue()
    }

    @Test
    fun `returnerer false når dirty eventer er nyere enn clean eventer`() {
        val feilRekkefolge = checker.sjekkFeilRekkefølge(listOf(
            punsjEvent(eksternVersjon = "2026-06-10T10:00:00", dirty = false),
            punsjEvent(eksternVersjon = "2026-06-10T11:00:00", dirty = true),
        ))

        assertThat(feilRekkefolge).isFalse()
    }

    private fun punsjEvent(eksternVersjon: String, dirty: Boolean): EventLagret {
        return EventLagret.K9Punsj(
            nøkkelId = 1L,
            eksternId = "ekstern-id",
            eksternVersjon = eksternVersjon,
            eventJson = "{}",
            opprettet = LocalDateTime.parse(eksternVersjon),
            dirty = dirty,
        )
    }
}
