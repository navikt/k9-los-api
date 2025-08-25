package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottakoghistorikk.punsj

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.EventRepositoryPerLinje
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjId
import no.nav.k9.los.nyoppgavestyring.feilhandtering.DuplikatDataException
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime

class EventRepositoryTest() : AbstractK9LosIntegrationTest() {

    @Test
    fun `teste skriv og les`() {
        val eventRepositoryPerLinje = get<EventRepositoryPerLinje>()
        val transactionalManager = get<TransactionalManager>()

        val event = PunsjEventDto(
            eksternId = PunsjId.fromString("eksternId"),
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
        var eventLagret = eventRepositoryPerLinje.lagre(event)!!
        assertThat(eventLagret.eventNrForOppgave).isEqualTo(0)
        assertThat(eventLagret.eventDto.status).isEqualTo(Oppgavestatus.AAPEN)

        var alleEventer = eventRepositoryPerLinje.hentAlleEventer("eksternId")
        assertThat(alleEventer.size).isEqualTo(1)

        val event2 = PunsjEventDto(
            eksternId = PunsjId.fromString("eksternId"),
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

        eventLagret = eventRepositoryPerLinje.lagre(event2)!!
        assertThat(eventLagret.eventNrForOppgave).isEqualTo(1)
        assertThat(eventLagret.eventDto.status).isEqualTo(Oppgavestatus.VENTER)

        alleEventer = eventRepositoryPerLinje.hentAlleEventer("eksternId")
        assertThat(alleEventer.size).isEqualTo(2)

        alleEventer = eventRepositoryPerLinje.hentAlleDirtyEventer("eksternId")
        assertThat(alleEventer.size).isEqualTo(2)

        transactionalManager.transaction { tx ->
            eventRepositoryPerLinje.fjernDirty("eksternId", 0, tx)
        }
        alleEventer = eventRepositoryPerLinje.hentAlleDirtyEventer("eksternId")
        assertThat(alleEventer.size).isEqualTo(1)
        assertThat(alleEventer.get(0).eventDto.status).isEqualTo(Oppgavestatus.VENTER)

        val førsteLagredeEvent = eventRepositoryPerLinje.hent("eksternId", 0)!!
        assertThat(førsteLagredeEvent.eventDto.status).isEqualTo(Oppgavestatus.AAPEN)
    }

    @Test
    fun `teste skriv unique constraint`() {
        val eventRepositoryPerLinje = get<EventRepositoryPerLinje>()
        val transactionalManager = get<TransactionalManager>()

        val event = PunsjEventDto(
            eksternId = PunsjId.fromString("eksternId"),
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
        eventRepositoryPerLinje.lagre(event)
        assertThrows(DuplikatDataException::class.java) {
            eventRepositoryPerLinje.lagre(event)
        }
    }

    @Test
    fun `teste historikkvask les og skriv`() {
        val eventRepositoryPerLinje = get<EventRepositoryPerLinje>()
        val transactionalManager = get<TransactionalManager>()

        val event = PunsjEventDto(
            eksternId = PunsjId.fromString("eksternId"),
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
        val eventLagret = eventRepositoryPerLinje.lagre(event)!!

        val event2 = PunsjEventDto(
            eksternId = PunsjId.fromString("eksternId"),
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
        eventRepositoryPerLinje.lagre(event2)

        var alleEventer = eventRepositoryPerLinje.hentAlleEventIderUtenVasketHistorikk()
        assertThat(alleEventer.size).isEqualTo(2)

        eventRepositoryPerLinje.markerVasketHistorikk(eventLagret)
        alleEventer = eventRepositoryPerLinje.hentAlleEventIderUtenVasketHistorikk()
        assertThat(alleEventer.size).isEqualTo(1)
        val uvasketEventLagret = eventRepositoryPerLinje.hent(alleEventer.get(0))!!
        assertThat(uvasketEventLagret.eventDto.status).isEqualTo(Oppgavestatus.VENTER)

        eventRepositoryPerLinje.nullstillHistorikkvask()
        alleEventer = eventRepositoryPerLinje.hentAlleEventIderUtenVasketHistorikk()
        assertThat(alleEventer.size).isEqualTo(2)
    }
}