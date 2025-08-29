package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.feilhandtering.DuplikatDataException
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertNull
import org.koin.test.get
import java.time.LocalDateTime
import java.util.UUID

class EventRepositoryPerLinjeForKonverteringTest() : AbstractK9LosIntegrationTest() {

    @Test
    fun `teste skriv og les`() {
        val punsjEventRepositoryPerLinje = get<PunsjEventRepositoryPerLinje>()
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

        var eventLagret = punsjEventRepositoryPerLinje.lagre(eventstring, 0)!!
        assertThat(eventLagret.eventNrForOppgave).isEqualTo(0)
        assertThat(eventLagret.eventDto.status).isEqualTo(Oppgavestatus.AAPEN)

        var alleEventer = punsjEventRepositoryPerLinje.hentAlleEventer(eksternId.toString())
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

        eventLagret = punsjEventRepositoryPerLinje.lagre(eventstring2, 1)!!
        assertThat(eventLagret.eventNrForOppgave).isEqualTo(1)
        assertThat(eventLagret.eventDto.status).isEqualTo(Oppgavestatus.VENTER)

        alleEventer = punsjEventRepositoryPerLinje.hentAlleEventer(eksternId.toString())
        assertThat(alleEventer.size).isEqualTo(2)

        alleEventer = punsjEventRepositoryPerLinje.hentAlleDirtyEventer(eksternId.toString())
        assertThat(alleEventer.size).isEqualTo(2)

        transactionalManager.transaction { tx ->
            punsjEventRepositoryPerLinje.fjernDirty(eksternId.toString(), 0, tx)
        }
        alleEventer = punsjEventRepositoryPerLinje.hentAlleDirtyEventer(eksternId.toString())
        assertThat(alleEventer.size).isEqualTo(1)
        assertThat(alleEventer.get(0).eventDto.status).isEqualTo(Oppgavestatus.VENTER)

        val førsteLagredeEvent = punsjEventRepositoryPerLinje.hent(eksternId.toString(), 0)!!
        assertThat(førsteLagredeEvent.eventDto.status).isEqualTo(Oppgavestatus.AAPEN)
    }

    @Test
    fun `teste skriv unique constraint`() {
        val punsjEventRepositoryPerLinje = get<PunsjEventRepositoryPerLinje>()
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

        punsjEventRepositoryPerLinje.lagre(eventString, 0)
        val retur = punsjEventRepositoryPerLinje.lagre(eventString, 1)
        assertNull(retur)
    }

    @Test
    fun `teste historikkvask les og skriv`() {
        val punsjEventRepositoryPerLinje = get<PunsjEventRepositoryPerLinje>()
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

        val eventLagret = punsjEventRepositoryPerLinje.lagre(eventString, 0)!!

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
        punsjEventRepositoryPerLinje.lagre(eventString2, 1)

        var alleEventer = punsjEventRepositoryPerLinje.hentAlleEventIderUtenVasketHistorikk()
        assertThat(alleEventer.size).isEqualTo(2)

        punsjEventRepositoryPerLinje.markerVasketHistorikk(eventLagret)
        alleEventer = punsjEventRepositoryPerLinje.hentAlleEventIderUtenVasketHistorikk()
        assertThat(alleEventer.size).isEqualTo(1)
        val uvasketEventLagret = punsjEventRepositoryPerLinje.hent(alleEventer.get(0))!!
        assertThat(uvasketEventLagret.eventDto.status).isEqualTo(Oppgavestatus.VENTER)

        punsjEventRepositoryPerLinje.nullstillHistorikkvask()
        alleEventer = punsjEventRepositoryPerLinje.hentAlleEventIderUtenVasketHistorikk()
        assertThat(alleEventer.size).isEqualTo(2)
    }
}