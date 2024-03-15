package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottakoghistorikk.punsj

import no.nav.k9.los.AbstractK9LosIntegrationTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.koin.test.get

class EventRepositoryTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `teste skriv og les`() {
        val eventRepository = get<EventRepository>()

        val event = PunsjEventV3Dto(
            eksternId =,
            journalpostId =,
            aktørId =,
            eventTid =,
            aksjonspunktKoderMedStatusListe =,
            pleietrengendeAktørId =,
            type =,
            ytelse =,
            sendtInn =,
            ferdigstiltAv =,
            mottattDato =,
            status =,
        )
        eventRepository.lagre(event)
    }
}