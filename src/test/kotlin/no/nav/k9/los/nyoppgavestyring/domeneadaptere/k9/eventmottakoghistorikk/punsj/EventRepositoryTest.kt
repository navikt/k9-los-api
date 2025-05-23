package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottakoghistorikk.punsj

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.feilhandtering.DuplikatDataException
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime

class EventRepositoryTest() : AbstractK9LosIntegrationTest() {

    @Test
    fun `teste skriv og les`() {
        val eventRepository = get<EventRepository>()
        val transactionalManager = get<TransactionalManager>()

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
        var eventLagret = eventRepository.lagre(event)!!
        assertThat(eventLagret.eventNrForOppgave).isEqualTo(0)
        assertThat(eventLagret.eventV3Dto.status).isEqualTo(Oppgavestatus.AAPEN)

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

        eventLagret = eventRepository.lagre(event2)!!
        assertThat(eventLagret.eventNrForOppgave).isEqualTo(1)
        assertThat(eventLagret.eventV3Dto.status).isEqualTo(Oppgavestatus.VENTER)

        alleEventer = eventRepository.hentAlleEventer("eksternId")
        assertThat(alleEventer.size).isEqualTo(2)

        alleEventer = eventRepository.hentAlleDirtyEventer("eksternId")
        assertThat(alleEventer.size).isEqualTo(2)

        transactionalManager.transaction { tx ->
            eventRepository.fjernDirty("eksternId", 0, tx)
        }
        alleEventer = eventRepository.hentAlleDirtyEventer("eksternId")
        assertThat(alleEventer.size).isEqualTo(1)
        assertThat(alleEventer.get(0).eventV3Dto.status).isEqualTo(Oppgavestatus.VENTER)

        val førsteLagredeEvent = eventRepository.hent("eksternId", 0)!!
        assertThat(førsteLagredeEvent.eventV3Dto.status).isEqualTo(Oppgavestatus.AAPEN)
    }

    @Test
    fun `teste skriv unique constraint`() {
        val eventRepository = get<EventRepository>()
        val transactionalManager = get<TransactionalManager>()

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
        assertThrows(DuplikatDataException::class.java) {
            eventRepository.lagre(event)
        }
    }

    @Test
    fun `teste historikkvask les og skriv`() {
        val eventRepository = get<EventRepository>()
        val transactionalManager = get<TransactionalManager>()

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
        val eventLagret = eventRepository.lagre(event)!!

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

        var alleEventer = eventRepository.hentAlleEventIderUtenVasketHistorikk()
        assertThat(alleEventer.size).isEqualTo(2)

        eventRepository.markerVasketHistorikk(eventLagret)
        alleEventer = eventRepository.hentAlleEventIderUtenVasketHistorikk()
        assertThat(alleEventer.size).isEqualTo(1)
        val uvasketEventLagret = eventRepository.hent(alleEventer.get(0))!!
        assertThat(uvasketEventLagret.eventV3Dto.status).isEqualTo(Oppgavestatus.VENTER)

        eventRepository.nullstillHistorikkvask()
        alleEventer = eventRepository.hentAlleEventIderUtenVasketHistorikk()
        assertThat(alleEventer.size).isEqualTo(2)
    }
}