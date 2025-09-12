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

        val eventLagret = transactionalManager.transaction { tx ->
            eventRepository.lagre(eventstring, Fagsystem.PUNSJ, tx)!!
        }
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

        val eventLagret2 = transactionalManager.transaction { tx ->
            eventRepository.lagre(eventstring2, Fagsystem.PUNSJ, tx)!!
        }
        assertThat(PunsjEventDto.fraEventLagret(eventLagret2).status).isEqualTo(Oppgavestatus.VENTER)

        alleEventer = eventRepository.hentAlleEventer(eksternId.toString())
        assertThat(alleEventer.size).isEqualTo(2)

        transactionalManager.transaction { tx ->
            alleEventer = eventRepository.hentAlleDirtyEventerMedLås(eksternId.toString(), Fagsystem.PUNSJ, tx)
        }
        assertThat(alleEventer.size).isEqualTo(2)

        alleEventer = transactionalManager.transaction { tx ->
            eventRepository.fjernDirty(eventLagret, tx)
            eventRepository.hentAlleDirtyEventerMedLås(eksternId.toString(), Fagsystem.PUNSJ, tx)
        }
        assertThat(alleEventer.size).isEqualTo(1)
        assertThat(PunsjEventDto.fraEventLagret(alleEventer[0]).status).isEqualTo(Oppgavestatus.VENTER)

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

        transactionalManager.transaction { tx ->
            eventRepository.lagre(eventString, Fagsystem.PUNSJ, tx)
            eventRepository.lagre(eventString, Fagsystem.PUNSJ, tx)
        }

        val retur = eventRepository.hentAlleEventer(eksternId.toString())

        assertThat(retur.size).isEqualTo(1)
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

        val eventLagret = transactionalManager.transaction { tx ->
            eventRepository.lagre(eventString, Fagsystem.PUNSJ, tx)!!
        }

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
        val eventLagret2 = transactionalManager.transaction { tx ->
            eventRepository.lagre(eventString2, Fagsystem.PUNSJ, tx)
        }

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