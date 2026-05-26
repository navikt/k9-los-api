package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjId
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker.EventlagerNokkeltallPrometheusCollector
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime
import java.util.*

// Test-lokal helper: gjør K9Punsj-spesifikk eventDto-aksess konsis uten å holde liv i
// en produksjonsmetode kun brukt fra tester.
private val EventLagret.punsjEventDto: K9PunsjEventDto
    get() = (this as EventLagret.K9Punsj).eventDto

class EventRepositoryPerLinjeForKonverteringTest() : AbstractK9LosIntegrationTest() {

    private fun leggTilOppgaveversjonForStatistikk(
        tx: TransactionalSession,
        oppgavetypeEksternId: String,
        eksternId: String,
        eksternVersjon: String,
        markertSomSendt: Boolean = false,
    ) {
        val oppgavetypeId = tx.run(
            queryOf(
                "select id from oppgavetype where ekstern_id = :ekstern_id",
                mapOf("ekstern_id" to oppgavetypeEksternId),
            ).map { row -> row.long("id") }.asSingle
        ) ?: error("Mangler oppgavetype $oppgavetypeEksternId i testgrunnlaget")

        tx.run(
            queryOf(
                """
                insert into oppgave_v3(
                    ekstern_id,
                    ekstern_versjon,
                    oppgavetype_id,
                    status,
                    kildeomrade,
                    versjon,
                    endret_tidspunkt,
                    reservasjonsnokkel,
                    oppgavetype_ekstern_id,
                    omrade_ekstern_id
                ) values (
                    :ekstern_id,
                    :ekstern_versjon,
                    :oppgavetype_id,
                    'AAPEN',
                    'K9',
                    1,
                    :endret_tidspunkt,
                    :reservasjonsnokkel,
                    :oppgavetype_ekstern_id,
                    'k9'
                )
                """.trimIndent(),
                mapOf(
                    "ekstern_id" to eksternId,
                    "ekstern_versjon" to eksternVersjon,
                    "oppgavetype_id" to oppgavetypeId,
                    "endret_tidspunkt" to LocalDateTime.now(),
                    "reservasjonsnokkel" to eksternId,
                    "oppgavetype_ekstern_id" to oppgavetypeEksternId,
                )
            ).asUpdate
        )

        if (markertSomSendt) {
            tx.run(
                queryOf(
                    """
                    insert into oppgave_v3_sendt_dvh_ekstern(ekstern_id, ekstern_versjon)
                    values (:ekstern_id, :ekstern_versjon)
                    """.trimIndent(),
                    mapOf(
                        "ekstern_id" to eksternId,
                        "ekstern_versjon" to eksternVersjon,
                    )
                ).asUpdate
            )
        }
    }

    @BeforeEach
    fun setup() {
        get<OmrådeSetup>().setup()
    }

    @Test
    fun `teste skriv og les`() {
        val eventRepository = get<EventRepository>()
        val transactionalManager = get<TransactionalManager>()

        val eksternId = PunsjId.fromString(UUID.randomUUID().toString())

        val event = K9PunsjEventDto(
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
            eventRepository.lagre(Fagsystem.PUNSJ, eksternId.toString(), event.eventTid.toString(), eventstring, tx)
            eventRepository.hent(Fagsystem.PUNSJ, eksternId.toString(), event.eventTid.toString(), tx)
        }

        assertThat(eventLagret.punsjEventDto.status).isEqualTo(Oppgavestatus.AAPEN)

        var alleEventer = transactionalManager.transaction { tx ->
            eventRepository.hentAlleEventerMedLås(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()), tx)
        }
        assertThat(alleEventer.size).isEqualTo(1)

        val event2 = K9PunsjEventDto(
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
            eventRepository.lagre(Fagsystem.PUNSJ, event2.eksternId.toString(), event2.eventTid.toString(), eventstring2, tx)
            eventRepository.hent(Fagsystem.PUNSJ, event2.eksternId.toString(), event2.eventTid.toString(), tx)
        }
        assertThat(eventLagret2.punsjEventDto.status).isEqualTo(Oppgavestatus.VENTER)

        alleEventer = transactionalManager.transaction { tx ->
            eventRepository.hentAlleEventerMedLås(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()), tx)
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
        assertThat(alleDirtyEventerNummerert[0].second.punsjEventDto.status).isEqualTo(Oppgavestatus.VENTER)

        val førsteLagredeEvent = transactionalManager.transaction { tx ->
            eventRepository.hent(
                Fagsystem.PUNSJ,
                eksternId.toString(),
                event.eventTid.toString(),
                tx
            )
        }
        assertThat(førsteLagredeEvent.punsjEventDto.status).isEqualTo(Oppgavestatus.AAPEN)
    }

    @Test
    fun `teste skriv unique constraint`() {
        val eventRepository = get<EventRepository>()
        val transactionalManager = get<TransactionalManager>()

        val eksternId = PunsjId.fromString(UUID.randomUUID().toString())

        val event = K9PunsjEventDto(
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
            eventRepository.lagre(Fagsystem.PUNSJ, eksternId.toString(), event.eventTid.toString(), eventString, tx)
            eventRepository.lagre(Fagsystem.PUNSJ, eksternId.toString(), event.eventTid.toString(), eventString, tx)
        }

        val retur = transactionalManager.transaction { tx ->
            eventRepository.hentAlleEventerMedLås(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()), tx)
        }

        assertThat(retur.size).isEqualTo(1)
    }

    @Test
    //Ignorerer testen midlertidig, siden bakenforliggende logikk midlertidig sjalter vekk punsj
    fun `teste historikkvask les og skriv`() {
        val eventRepository = get<EventRepository>()
        val transactionalManager = get<TransactionalManager>()

        val eksternId = PunsjId.fromString(UUID.randomUUID().toString())

        val event = K9PunsjEventDto(
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

        transactionalManager.transaction { tx ->
            eventRepository.lagre(Fagsystem.PUNSJ, event.eksternId.toString(), event.eventTid.toString(), eventString, tx)
        }

        val event2 = K9PunsjEventDto(
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
            eventRepository.lagre(Fagsystem.PUNSJ, event2.eksternId.toString(), event2.eventTid.toString(), eventString2, tx)
        }

        eventRepository.bestillHistorikkvask(Fagsystem.PUNSJ)
        var vaskeBestillinger = eventRepository.hentAlleHistorikkvaskbestillinger()
        assertThat(vaskeBestillinger.size).isEqualTo(2)

        transactionalManager.transaction { tx ->
            eventRepository.settHistorikkvaskFerdig(Fagsystem.PUNSJ, event.eksternId.toString(), tx)
        }
        vaskeBestillinger = eventRepository.hentAlleHistorikkvaskbestillinger()
        assertThat(vaskeBestillinger.size).isEqualTo(1)
        val uvasketEventLagret = transactionalManager.transaction { tx ->
            eventRepository.hentAlleEventerMedLås(
                EventNøkkel(Fagsystem.PUNSJ, vaskeBestillinger.get(0).eksternId), tx
            )
                .sortedBy { LocalDateTime.parse(it.eksternVersjon) }[0]
        }
        assertThat(uvasketEventLagret.punsjEventDto.status).isEqualTo(Oppgavestatus.VENTER)

        transactionalManager.transaction { tx ->
            eventRepository.settHistorikkvaskFerdig(Fagsystem.PUNSJ, uvasketEventLagret.eksternId, tx)
        }

        vaskeBestillinger = eventRepository.hentAlleHistorikkvaskbestillinger()
        assertThat(vaskeBestillinger).isEmpty()

        transactionalManager.transaction { tx ->
            eventRepository.bestillHistorikkvask(Fagsystem.PUNSJ, event.eksternId.toString(), tx)
        }

        vaskeBestillinger = eventRepository.hentAlleHistorikkvaskbestillinger()
        assertThat(vaskeBestillinger.size).isEqualTo(1)
    }

    @Test
    fun `henter nøkkeltall per fagsystem for dirty eventer og historikkvaskbestillinger`() {
        val eventRepository = get<EventRepository>()
        val transactionalManager = get<TransactionalManager>()

        val punsjEksternId = UUID.randomUUID().toString()
        val sakEksternId = UUID.randomUUID().toString()
        val klageEksternId = UUID.randomUUID().toString()

        transactionalManager.transaction { tx ->
            eventRepository.lagre(Fagsystem.PUNSJ, punsjEksternId, LocalDateTime.now().minusMinutes(2).toString(), "{}", tx)
            eventRepository.lagre(Fagsystem.PUNSJ, punsjEksternId, LocalDateTime.now().minusMinutes(1).toString(), "{}", tx)
            val sakNøkkel = eventRepository.lagre(Fagsystem.K9SAK, sakEksternId, LocalDateTime.now().toString(), "{}", tx)
            eventRepository.lagre(Fagsystem.K9KLAGE, klageEksternId, LocalDateTime.now().plusMinutes(1).toString(), "{}", tx)

            eventRepository.fjernAlleDirty(sakNøkkel.id!!, tx)

            eventRepository.bestillHistorikkvask(Fagsystem.PUNSJ, punsjEksternId, tx)
            eventRepository.bestillHistorikkvask(Fagsystem.K9KLAGE, klageEksternId, tx)
            eventRepository.bestillHistorikkvask(Fagsystem.K9SAK, sakEksternId, tx)
            eventRepository.settHistorikkvaskFerdig(Fagsystem.K9SAK, sakEksternId, tx)

            leggTilOppgaveversjonForStatistikk(tx, "k9sak", UUID.randomUUID().toString(), "1")
            leggTilOppgaveversjonForStatistikk(tx, "k9sak", UUID.randomUUID().toString(), "2", markertSomSendt = true)
            leggTilOppgaveversjonForStatistikk(tx, "k9klage", UUID.randomUUID().toString(), "1")
        }

        val dirtyPerFagsystem = eventRepository.hentAntallDirtyEventerPerFagsystem().associate { it.fagsystem to it.antall }
        assertThat(dirtyPerFagsystem).isEqualTo(mapOf("K9KLAGE" to 1L, "PUNSJ" to 2L))

        val dirtyEventnoklerPerFagsystem = eventRepository.hentAntallDirtyEventnoklerPerFagsystem().associate { it.fagsystem to it.antall }
        assertThat(dirtyEventnoklerPerFagsystem).isEqualTo(mapOf("K9KLAGE" to 1L, "PUNSJ" to 1L))

        val historikkvaskPerFagsystem = eventRepository.hentAntallHistorikkvaskbestillingerPerFagsystem().associate { it.fagsystem to it.antall }
        assertThat(historikkvaskPerFagsystem).isEqualTo(mapOf("K9KLAGE" to 1L, "PUNSJ" to 1L))

        val usendtOppgavestatistikkPerFagsystem = eventRepository.hentAntallUsendtOppgavestatistikkPerFagsystem().associate { it.fagsystem to it.antall }
        assertThat(usendtOppgavestatistikkPerFagsystem).isEqualTo(mapOf("K9KLAGE" to 1L, "K9SAK" to 1L))
    }

    @Test
    fun `eksponerer prometheus metrikker for eventlager per fagsystem`() {
        val eventRepository = get<EventRepository>()
        val transactionalManager = get<TransactionalManager>()

        val punsjEksternId = UUID.randomUUID().toString()
        val klageEksternId = UUID.randomUUID().toString()

        transactionalManager.transaction { tx ->
            eventRepository.lagre(Fagsystem.PUNSJ, punsjEksternId, LocalDateTime.now().minusMinutes(1).toString(), "{}", tx)
            eventRepository.lagre(Fagsystem.PUNSJ, punsjEksternId, LocalDateTime.now().toString(), "{}", tx)
            eventRepository.lagre(Fagsystem.K9KLAGE, klageEksternId, LocalDateTime.now().toString(), "{}", tx)
            eventRepository.bestillHistorikkvask(Fagsystem.PUNSJ, punsjEksternId, tx)

            leggTilOppgaveversjonForStatistikk(tx, "k9sak", UUID.randomUUID().toString(), "1")
            leggTilOppgaveversjonForStatistikk(tx, "k9sak", UUID.randomUUID().toString(), "2", markertSomSendt = true)
            leggTilOppgaveversjonForStatistikk(tx, "k9klage", UUID.randomUUID().toString(), "1")
        }

        val collector = EventlagerNokkeltallPrometheusCollector(eventRepository, registerCollector = false)
        val metrics = collector.collect().associateBy { it.name }

        val dirtySamples = metrics.getValue("k9los_eventlager_dirty_eventer").samples
        val dirtyEventnokkelSamples = metrics.getValue("k9los_eventlager_dirty_eventnokler").samples
        val historikkvaskSamples = metrics.getValue("k9los_eventlager_historikkvask_bestillinger").samples
        val usendtOppgavestatistikkSamples = metrics.getValue("k9los_eventlager_oppgavestatistikk_usendt").samples

        fun sampleValue(samples: List<io.prometheus.client.Collector.MetricFamilySamples.Sample>, fagsystem: String): Double {
            return samples.first { it.labelValues == listOf(fagsystem) }.value
        }

        assertThat(sampleValue(dirtySamples, "PUNSJ")).isEqualTo(2.0)
        assertThat(sampleValue(dirtySamples, "K9KLAGE")).isEqualTo(1.0)
        assertThat(sampleValue(dirtyEventnokkelSamples, "PUNSJ")).isEqualTo(1.0)
        assertThat(sampleValue(dirtyEventnokkelSamples, "K9KLAGE")).isEqualTo(1.0)
        assertThat(sampleValue(historikkvaskSamples, "PUNSJ")).isEqualTo(1.0)
        assertThat(sampleValue(historikkvaskSamples, "K9KLAGE")).isEqualTo(0.0)
        assertThat(sampleValue(usendtOppgavestatistikkSamples, "K9SAK")).isEqualTo(1.0)
        assertThat(sampleValue(usendtOppgavestatistikkSamples, "K9KLAGE")).isEqualTo(1.0)
        assertThat(sampleValue(usendtOppgavestatistikkSamples, "PUNSJ")).isEqualTo(0.0)
    }
}