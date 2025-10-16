package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import kotliquery.queryOf
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import org.koin.test.KoinTest
import org.koin.test.get
import java.time.LocalDateTime
import java.util.*

class HistorikkvaskTjenesteSpec: FreeSpec(), KoinTest {
    val transactionalManager = get<TransactionalManager>()
    val eventRepository = get<EventRepository>()
    val oppgaveAdapter = get<EventTilOppgaveAdapter>()
    val historikkvaskTjeneste = get<HistorikkvaskTjeneste>()

    init {
        "Tre oppgaveeventer, sortert etter eventTid" - {
            val eksternId = UUID.randomUUID()
            val event = punsjEvent(eksternId, LocalDateTime.now().minusHours(2))
            val event2 = punsjEvent(eksternId, LocalDateTime.now().minusHours(1))
            val event3 = punsjEvent(eksternId, LocalDateTime.now())
            "hvor event nr 1 og 3 er innlest først" - {
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event), tx)
                    eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event3), tx)
                }
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(null, Fagsystem.PUNSJ, eksternId.toString()))
                "og event nr 2 er innlest etterpå" - {
                    transactionalManager.transaction { tx ->
                        eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event2), tx)
                    }
                    oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(null, Fagsystem.PUNSJ, eksternId.toString()))
                    "skal kunne korrigeres med historikkvask" {
                        eventRepository.hentAntallHistorikkvaskbestillinger() shouldBe 1

                        val uvasketHistorikk = hentOppgavehistorikk(eksternId.toString())
                        uvasketHistorikk.size shouldBe 3
                        uvasketHistorikk.sortedBy { it.second } shouldNotBeEqual uvasketHistorikk.sortedBy { it.third }

                        historikkvaskTjeneste.kjørHistorikkvask()

                        val vasketHistorikk = hentOppgavehistorikk(eksternId.toString())
                        vasketHistorikk.size shouldBe 3
                        vasketHistorikk.sortedBy { it.second } shouldBeEqual vasketHistorikk.sortedBy { it.third }
                    }
                }
            }
        }
    }

    fun hentOppgavehistorikk(eksternId: String): List<Triple<String, LocalDateTime, Int>> {
        return transactionalManager.transaction { tx ->
            tx.run (
                queryOf(
                    """
                        select ov.ekstern_id, ov.ekstern_versjon, ov.versjon 
                        from oppgave_v3 ov
                        where ov.ekstern_id = :eksternId
                    """.trimIndent(),
                    mapOf("eksternId" to eksternId)
                ).map { row ->
                    Triple(
                        row.string("ekstern_id"),
                        LocalDateTime.parse(row.string("ekstern_versjon")),
                        row.int("versjon")
                    )
                }.asList
            )
        }
    }

    fun punsjEvent(eksternId: UUID = UUID.randomUUID(),
                   eksternVersjon: LocalDateTime = LocalDateTime.now().minusHours(1))
            : PunsjEventDto {
        return PunsjEventDto(
            eksternId = eksternId,
            journalpostId = JournalpostId(1L),
            eventTid = eksternVersjon,
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
    }
}