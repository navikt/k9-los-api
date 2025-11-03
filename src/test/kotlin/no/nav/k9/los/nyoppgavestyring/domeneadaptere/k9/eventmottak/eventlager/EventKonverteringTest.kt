package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.AnnotationSpec
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventHandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjId
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import org.koin.test.KoinTest
import org.koin.test.get
import java.time.LocalDateTime
import java.util.*

class EventKonverteringTest() : KoinTest, AnnotationSpec() {

    @Test
    fun testKonverteringsjobb() {
        val eventHandler = get<K9PunsjEventHandler>()
        val eventRepository = get<EventRepository>()
        val punsjEventRepository = get<K9PunsjEventRepository>()
        val transactionalManager = get<TransactionalManager>()
        val konverteringsjobb = get<EventlagerKonverteringsjobb>()

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

        val event3 = PunsjEventDto(
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

        punsjEventRepository.lagre(event)
        punsjEventRepository.lagre(event2)
        punsjEventRepository.lagre(event3)

        val eksternId2 = PunsjId.fromString(UUID.randomUUID().toString())
        val event21 = PunsjEventDto(
            eksternId = eksternId2,
            journalpostId = JournalpostId(1L),
            eventTid = LocalDateTime.now().minusHours(2),
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

        val event22 = PunsjEventDto(
            eksternId = eksternId2,
            journalpostId = JournalpostId(1L),
            aktørId = AktørId(2L),
            eventTid = LocalDateTime.now().minusHours(1),
            status = Oppgavestatus.VENTER,
            aksjonspunktKoderMedStatusListe = mutableMapOf(),
            pleietrengendeAktørId = "pleietrengendeAktørId",
            type = "type",
            ytelse = "ytelse",
            sendtInn = false,
            ferdigstiltAv = "saksbehandler",
            journalførtTidspunkt = LocalDateTime.now().minusDays(1),
        )

        val event23 = PunsjEventDto(
            eksternId = eksternId2,
            journalpostId = JournalpostId(1L),
            aktørId = AktørId(2L),
            eventTid = LocalDateTime.now(),
            status = Oppgavestatus.LUKKET,
            aksjonspunktKoderMedStatusListe = mutableMapOf(),
            pleietrengendeAktørId = "pleietrengendeAktørId",
            type = "type",
            ytelse = "ytelse",
            sendtInn = false,
            ferdigstiltAv = "saksbehandler",
            journalførtTidspunkt = LocalDateTime.now().minusDays(1),
        )

        punsjEventRepository.lagre(event21)
        punsjEventRepository.lagre(event22)

        eventHandler.prosesser(event23)

        konverteringsjobb.spillAvEventer()

        val konverterteEventer = transactionalManager.transaction { tx ->
            eventRepository.hentAlleEventer(Fagsystem.PUNSJ, eksternId.toString(), tx)
        }

        val konverterteEventer2 = transactionalManager.transaction { tx ->
            eventRepository.hentAlleEventer(Fagsystem.PUNSJ, eksternId2.toString(), tx)
        }

        assertThat(konverterteEventer).hasSize(3)
        assertThat(konverterteEventer2).hasSize(3)
    }

    @Test
    fun testLivekonvertering() {
        val eventHandler = get<K9PunsjEventHandler>()
        val eventRepository = get<EventRepository>()
        val punsjEventRepository = get<K9PunsjEventRepository>()
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

        val event3 = PunsjEventDto(
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

        //eventHandler.prosesser(event)
        punsjEventRepository.lagre(event)
        punsjEventRepository.lagre(event2)


        val exception = shouldThrow<NullPointerException> {
            transactionalManager.transaction { tx ->
                eventRepository.hentAlleEventerMedLås(Fagsystem.PUNSJ, eksternId.toString(), tx)
            }
        }

        eventHandler.prosesser(event2)

        var eventerLagret = transactionalManager.transaction { tx ->
            eventRepository.hentAlleEventerMedLås(Fagsystem.PUNSJ, eksternId.toString(), tx)
        }

        assertThat(eventerLagret.size).isEqualTo(1)

        eventHandler.prosesser(event3)

        eventerLagret = transactionalManager.transaction { tx ->
            eventRepository.hentAlleEventerMedLås(Fagsystem.PUNSJ, eksternId.toString(), tx)
        }

        assertThat(eventerLagret.size).isEqualTo(2)
    }
}