package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottakoghistorikk.punsj

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime

class EventRepositoryTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `teste skriv og les`() {
        val eventRepository = get<EventRepository>()

        val event = PunsjEventV3Dto(
            eksternId = "eksternId",
            journalpostId = "journalpostId",
            aktørId = "aktørId",
            eventTid = LocalDateTime.now().minusHours(1),
            aksjonspunktKoderMedStatusListe = emptyMap(),
            pleietrengendeAktørId = "pleietrengendeAktørId",
            type = "type",
            ytelse = "ytelse",
            sendtInn = false,
            ferdigstiltAv = "saksbehandler",
            mottattDato = LocalDateTime.now().minusDays(1),
            status = Oppgavestatus.AAPEN,
        )
        eventRepository.lagre(event)

        var alleEventer = eventRepository.hentAlleEventer("eksternId")
        assertThat(alleEventer.size).isEqualTo(1)

        val event2 = PunsjEventV3Dto(
            eksternId = "eksternId",
            journalpostId = "journalpostId",
            aktørId = "aktørId",
            eventTid = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = emptyMap(),
            pleietrengendeAktørId = "pleietrengendeAktørId",
            type = "type",
            ytelse = "ytelse",
            sendtInn = false,
            ferdigstiltAv = "saksbehandler",
            mottattDato = LocalDateTime.now().minusDays(1),
            status = Oppgavestatus.VENTER,
        )

        eventRepository.lagre(event2)

        alleEventer = eventRepository.hentAlleEventer("eksternId")
        assertThat(alleEventer.size).isEqualTo(2)

        alleEventer = eventRepository.hentAlleDirtyEventer("eksternId")
        assertThat(alleEventer.size).isEqualTo(2)

        val førsteLagredeEvent = eventRepository.hent("eksternId", 0)
        assertThat(førsteLagredeEvent.status).isEqualTo(Oppgavestatus.AAPEN)
    }
}