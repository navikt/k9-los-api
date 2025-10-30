package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import io.kotest.core.spec.style.AnnotationSpec
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjId
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

class EventRepositoryPerLinjeForKonverteringTest() : AbstractK9LosIntegrationTest() {

    @BeforeEach
    fun setup() {
        get<OmrådeSetup>().setup()
    }

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
            eventRepository.lagre(Fagsystem.PUNSJ, eventstring, tx)!!
        }

        assertThat(PunsjEventDto.fraEventLagret(eventLagret).status).isEqualTo(Oppgavestatus.AAPEN)

        var alleEventer = transactionalManager.transaction { tx ->
            eventRepository.hentAlleEventerMedLås(Fagsystem.PUNSJ, eksternId.toString(), tx)
        }
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
            eventRepository.lagre(Fagsystem.PUNSJ, eventstring2, tx)!!
        }
        assertThat(PunsjEventDto.fraEventLagret(eventLagret2).status).isEqualTo(Oppgavestatus.VENTER)

        alleEventer = transactionalManager.transaction { tx ->
            eventRepository.hentAlleEventerMedLås(Fagsystem.PUNSJ, eksternId.toString(), tx)
        }
        assertThat(alleEventer.size).isEqualTo(2)

        var alleDirtyEventerNummerert = transactionalManager.transaction { tx ->
            eventRepository.hentAlleDirtyEventerNummerertMedLås(Fagsystem.PUNSJ, eksternId.toString(), tx)
        }
        assertThat(alleDirtyEventerNummerert.size).isEqualTo(2)

        alleDirtyEventerNummerert = transactionalManager.transaction { tx ->
            eventRepository.fjernDirty(eventLagret, tx)
            eventRepository.hentAlleDirtyEventerNummerertMedLås(Fagsystem.PUNSJ, eksternId.toString(), tx)
        }
        assertThat(alleDirtyEventerNummerert.size).isEqualTo(1)
        assertThat(PunsjEventDto.fraEventLagret(alleDirtyEventerNummerert[0].second).status).isEqualTo(Oppgavestatus.VENTER)

        val førsteLagredeEvent = transactionalManager.transaction { tx ->
            eventRepository.hent(
                Fagsystem.PUNSJ,
                eksternId.toString(),
                event.eventTid.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                tx
            )
        }
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
            eventRepository.lagre(Fagsystem.PUNSJ, eventString, tx)
            eventRepository.lagre(Fagsystem.PUNSJ, eventString, tx)
        }

        val retur = transactionalManager.transaction { tx ->
            eventRepository.hentAlleEventerMedLås(Fagsystem.PUNSJ, eksternId.toString(), tx)
        }

        assertThat(retur.size).isEqualTo(1)
    }

    /*
    @Test
    //Ignorerer testen midlertidig, siden bakenforliggende logikk midlertidig sjalter vekk punsj
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
            eventRepository.lagre(Fagsystem.PUNSJ, eventString, tx)!!
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
            eventRepository.lagre(Fagsystem.PUNSJ, eventString2, tx)
        }

        eventRepository.bestillHistorikkvask(Fagsystem.PUNSJ)
        var vaskeBestillinger = eventRepository.hentAlleHistorikkvaskbestillinger()
        assertThat(vaskeBestillinger.size).isEqualTo(2)

        eventRepository.settHistorikkvaskFerdig(Fagsystem.PUNSJ, eventLagret.eksternId)
        vaskeBestillinger = eventRepository.hentAlleHistorikkvaskbestillinger()
        assertThat(vaskeBestillinger.size).isEqualTo(1)
        val uvasketEventLagret = transactionalManager.transaction { tx ->
            eventRepository.hentAlleEventerMedLås(
                Fagsystem.PUNSJ,
                vaskeBestillinger.get(0).eksternId, tx
            )
                .sortedBy { LocalDateTime.parse(it.eksternVersjon) }[0]
        }
        assertThat(PunsjEventDto.fraEventLagret(uvasketEventLagret).status).isEqualTo(Oppgavestatus.VENTER)

        eventRepository.settHistorikkvaskFerdig(Fagsystem.PUNSJ, uvasketEventLagret.eksternId)

        vaskeBestillinger = eventRepository.hentAlleHistorikkvaskbestillinger()
        assertThat(vaskeBestillinger).isEmpty()

        transactionalManager.transaction { tx ->
            eventRepository.bestillHistorikkvask(Fagsystem.PUNSJ, event.eksternId.toString(), tx)
        }

        vaskeBestillinger = eventRepository.hentAlleHistorikkvaskbestillinger()
        assertThat(vaskeBestillinger.size).isEqualTo(1)
    }
     */

}