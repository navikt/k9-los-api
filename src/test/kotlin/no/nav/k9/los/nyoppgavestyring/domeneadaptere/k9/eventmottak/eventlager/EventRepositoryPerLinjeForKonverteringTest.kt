package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjId
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.koin.test.get
import java.time.LocalDateTime
import java.util.UUID

class EventRepositoryPerLinjeForKonverteringTest() : AbstractK9LosIntegrationTest() {

    @Test
    fun `teste skriv og les`() {
        val eventRepository = get<EventRepository>()
        val transactionalManager = get<TransactionalManager>()

        val eksternId = PunsjId.fromString(UUID.randomUUID().toString())

        val event = PunsjEventDto(
            eksternId = eksternId,
            journalpostId = JournalpostId(1L),
            eventTid = LocalDateTime.now().minusHours(1),
            status = Oppgavestatus.AAPEN,
            aktørId = AktørId(2L),
            aksjonspunktKoderMedStatusListe = mutableMapOf(),
            pleietrengendeAktørId = "pleietrengendeAktørId",
            type = "type",
            ytelse = "ytelse",
            sendtInn = false,
            ferdigstiltAv = "saksbehandler",
            journalførtTidspunkt = LocalDateTime.now().minusDays(1),
        )
        val eventstring = LosObjectMapper.instance.writeValueAsString(event)

        var eventLagret = eventRepository.lagre(eventstring, Fagsystem.PUNSJ, 0)!!
        assertThat(eventLagret.eventNrForOppgave).isEqualTo(0)
        assertThat(PunsjEventDto.fraEventLagret(eventLagret).status).isEqualTo(Oppgavestatus.AAPEN)

        var alleEventer = eventRepository.hentAlleEventer(eksternId.toString())
        assertThat(alleEventer.size).isEqualTo(1)

        val event2 = PunsjEventDto(
            eksternId = eksternId,
            journalpostId = JournalpostId(1L),
            aktørId = AktørId(2L),
            eventTid = LocalDateTime.now(),
            status = Oppgavestatus.VENTER,
            aksjonspunktKoderMedStatusListe = mutableMapOf(),
            pleietrengendeAktørId = "pleietrengendeAktørId",
            type = "type",
            ytelse = "ytelse",
            sendtInn = false,
            ferdigstiltAv = "saksbehandler",
            journalførtTidspunkt = LocalDateTime.now().minusDays(1),
        )
        val eventstring2 = LosObjectMapper.instance.writeValueAsString(event2)

        eventLagret = eventRepository.lagre(eventstring2, Fagsystem.PUNSJ, 1)!!
        assertThat(eventLagret.eventNrForOppgave).isEqualTo(1)
        assertThat(PunsjEventDto.fraEventLagret(eventLagret).status).isEqualTo(Oppgavestatus.VENTER)

        alleEventer = eventRepository.hentAlleEventer(eksternId.toString())
        assertThat(alleEventer.size).isEqualTo(2)

        alleEventer = eventRepository.hentAlleDirtyEventer(eksternId.toString())
        assertThat(alleEventer.size).isEqualTo(2)

        transactionalManager.transaction { tx ->
            eventRepository.fjernDirty(eksternId.toString(), 0, tx)
        }
        alleEventer = eventRepository.hentAlleDirtyEventer(eksternId.toString())
        assertThat(alleEventer.size).isEqualTo(1)
        assertThat(PunsjEventDto.fraEventLagret(eventLagret).status).isEqualTo(Oppgavestatus.VENTER)

        val førsteLagredeEvent = eventRepository.hent(eksternId.toString(), 0)!!
        assertThat(PunsjEventDto.fraEventLagret(førsteLagredeEvent).status).isEqualTo(Oppgavestatus.AAPEN)
    }

    @Test
    fun `teste skriv unique constraint`() {
        val eventRepository = get<EventRepository>()
        val transactionalManager = get<TransactionalManager>()

        val eksternId = PunsjId.fromString(UUID.randomUUID().toString())

        val event = PunsjEventDto(
            eksternId = eksternId,
            journalpostId = JournalpostId(1L),
            eventTid = LocalDateTime.now().minusHours(1),
            status = Oppgavestatus.AAPEN,
            aktørId = AktørId(2L),
            aksjonspunktKoderMedStatusListe = mutableMapOf(),
            pleietrengendeAktørId = "pleietrengendeAktørId",
            type = "type",
            ytelse = "ytelse",
            sendtInn = false,
            ferdigstiltAv = "saksbehandler",
            journalførtTidspunkt = LocalDateTime.now().minusDays(1),
        )

        val eventString = LosObjectMapper.instance.writeValueAsString(event)

        eventRepository.lagre(eventString, Fagsystem.PUNSJ, 0)
        val retur = eventRepository.lagre(eventString, Fagsystem.PUNSJ, 1)
        assertNull(retur)
    }

    @Test
    fun `teste historikkvask les og skriv`() {
        val eventRepository = get<EventRepository>()
        val transactionalManager = get<TransactionalManager>()

        val eksternId = PunsjId.fromString(UUID.randomUUID().toString())

        val event = PunsjEventDto(
            eksternId = PunsjId.fromString(UUID.randomUUID().toString()),
            journalpostId = JournalpostId(1L),
            eventTid = LocalDateTime.now().minusHours(1),
            status = Oppgavestatus.AAPEN,
            aktørId = AktørId(2L),
            aksjonspunktKoderMedStatusListe = mutableMapOf(),
            pleietrengendeAktørId = "pleietrengendeAktørId",
            type = "type",
            ytelse = "ytelse",
            sendtInn = false,
            ferdigstiltAv = "saksbehandler",
            journalførtTidspunkt = LocalDateTime.now().minusDays(1),
        )
        val eventString = LosObjectMapper.instance.writeValueAsString(event)

        val eventLagret = eventRepository.lagre(eventString, Fagsystem.PUNSJ,0)!!

        val event2 = PunsjEventDto(
            eksternId = eksternId,
            journalpostId = JournalpostId(1L),
            aktørId = AktørId(2L),
            eventTid = LocalDateTime.now(),
            status = Oppgavestatus.VENTER,
            aksjonspunktKoderMedStatusListe = mutableMapOf(),
            pleietrengendeAktørId = "pleietrengendeAktørId",
            type = "type",
            ytelse = "ytelse",
            sendtInn = false,
            ferdigstiltAv = "saksbehandler",
            journalførtTidspunkt = LocalDateTime.now().minusDays(1),
        )
        val eventString2 = LosObjectMapper.instance.writeValueAsString(event2)
        val eventLagret2 = eventRepository.lagre(eventString2, Fagsystem.PUNSJ, 1)

        var alleEventer = eventRepository.hentAlleEventIderUtenVasketHistorikk()
        assertThat(alleEventer.size).isEqualTo(2)

        eventRepository.markerVasketHistorikk(eventLagret)
        alleEventer = eventRepository.hentAlleEventIderUtenVasketHistorikk()
        assertThat(alleEventer.size).isEqualTo(1)
        val uvasketEventLagret = eventRepository.hent(alleEventer.get(0))!!
        assertThat(PunsjEventDto.fraEventLagret(uvasketEventLagret).status).isEqualTo(Oppgavestatus.VENTER)

        eventRepository.nullstillHistorikkvask()
        alleEventer = eventRepository.hentAlleEventIderUtenVasketHistorikk()
        assertThat(alleEventer.size).isEqualTo(2)
    }
}