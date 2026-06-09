package no.nav.k9.los.domeneadaptere.k9.eventtiloppgave

import io.kotest.core.spec.IsolationMode
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.equals.shouldBeEqual
import io.kotest.matchers.equals.shouldNotBeEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotliquery.queryOf
import no.nav.k9.los.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.HistorikkvaskBestilling
import no.nav.k9.los.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventDto
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.kodeverk.Fagsystem
import no.nav.k9.los.oppgavemottak.OppgaveV3Tjeneste
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
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
    val oppgaveTjeneste = get<OppgaveV3Tjeneste>()

    override fun isolationMode() = IsolationMode.InstancePerLeaf

    init {
        "En oppgaveversjon innlest i oppgavemodellen" - {
            val eksternId = UUID.randomUUID()
            val event = punsjEvent(eksternId, LocalDateTime.now().minusHours(2))
            val eventnøkkel = transactionalManager.transaction { tx ->
                eventRepository.lagre(Fagsystem.PUNSJ, event, tx)
            }
            oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()))
            "med feil i oppgavefeltverdier" - {
                val eventKorrigert = LosObjectMapper.instance.writeValueAsString(event.copy(ytelse = "ytelsekorrigert"))
                transactionalManager.transaction { tx ->
                    eventRepository.endreEvent(eventnøkkel, eventKorrigert, tx)
                }
                "skal få korrigerte verdier av historikkvasker" {
                    val oppgaveUvasket = transactionalManager.transaction { tx ->
                        eventRepository.bestillHistorikkvask(Fagsystem.PUNSJ)
                        oppgaveTjeneste.hentAktivOppgave(eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                    }
                    oppgaveUvasket.hentVerdi("ytelsestype") shouldBe "ytelse"
                    oppgaveUvasket.felter shouldHaveSize 12

                    historikkvaskTjeneste.vaskBestilling(HistorikkvaskBestilling(null, eksternId.toString(), Fagsystem.PUNSJ))

                    val oppgaveVasket = transactionalManager.transaction { tx ->
                        oppgaveTjeneste.hentAktivOppgave(eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                    }
                    oppgaveVasket.hentVerdi("ytelsestype") shouldBe "ytelsekorrigert"
                    oppgaveVasket.felter shouldHaveSize 12
                }
            }
        }
        "Tre oppgaveeventer, sortert etter eventTid" - {
            val eksternId = UUID.randomUUID()
            val event = punsjEvent(eksternId, LocalDateTime.now().minusHours(2))
            val event2 = punsjEvent(eksternId, LocalDateTime.now().minusHours(1))
            val event3 = punsjEvent(eksternId, LocalDateTime.now())
            "hvor event nr 1 og 3 er innlest først" - {
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, event, tx)
                    eventRepository.lagre(Fagsystem.PUNSJ, event3, tx)
                }
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()))
                "og event nr 2 er innlest etterpå" - {
                    transactionalManager.transaction { tx ->
                        eventRepository.lagre(Fagsystem.PUNSJ, event2, tx)
                    }
                    oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()))
                    "skal kunne korrigeres med historikkvask" {
                        eventRepository.hentAntallHistorikkvaskbestillinger() shouldBe 1

                        val uvasketHistorikk = hentOppgavehistorikk(eksternId.toString())
                        uvasketHistorikk.size shouldBe 3
                        uvasketHistorikk.sortedBy { it.second } shouldNotBeEqual uvasketHistorikk.sortedBy { it.third }

                        historikkvaskTjeneste.vaskBestilling(HistorikkvaskBestilling(null, eksternId.toString(), Fagsystem.PUNSJ))

                        val vasketHistorikk = hentOppgavehistorikk(eksternId.toString())
                        vasketHistorikk.size shouldBe 3
                        vasketHistorikk.sortedBy { it.second } shouldBeEqual vasketHistorikk.sortedBy { it.third }
                    }
                }
            }
        }
        "Historikkvaskbestillinger hentet via EventRepository" - {
            val eksternId1 = UUID.randomUUID()
            val eksternId2 = UUID.randomUUID()
            val event1 = punsjEvent(eksternId1, LocalDateTime.now().minusHours(2))
            val event2 = punsjEvent(eksternId2, LocalDateTime.now().minusHours(1))
            
            transactionalManager.transaction { tx ->
                eventRepository.lagre(Fagsystem.PUNSJ, event1, tx)
                eventRepository.lagre(Fagsystem.PUNSJ, event2, tx)
            }
            oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId1.toString()))
            oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId2.toString()))
            
            "skal kunne vaskes med eventlagerNøkkel fra bestillingen" {
                eventRepository.bestillHistorikkvask(Fagsystem.PUNSJ)
                eventRepository.hentAntallHistorikkvaskbestillinger() shouldBe 2
                
                val bestillinger = eventRepository.hentAlleHistorikkvaskbestillinger(antall = 10)
                bestillinger shouldHaveSize 2
                bestillinger.forEach { it.eventlagerNøkkel shouldNotBe null }
                
                // Vask første bestilling med eventlagerNøkkel
                val førsteBestilling = bestillinger.first()
                historikkvaskTjeneste.vaskBestilling(førsteBestilling)
                
                // Verifiser at kun én bestilling er fjernet
                eventRepository.hentAntallHistorikkvaskbestillinger() shouldBe 1
                
                // Vask andre bestilling
                val andreBestilling = bestillinger.last()
                historikkvaskTjeneste.vaskBestilling(andreBestilling)
                
                // Verifiser at alle bestillinger er fjernet
                eventRepository.hentAntallHistorikkvaskbestillinger() shouldBe 0
            }
        }
        "Cursor-basert batch-prosessering i kjørHistorikkvask" - {
            val eksternIder = (1..5).map { UUID.randomUUID() }
            val eventer = eksternIder.map { punsjEvent(it, LocalDateTime.now().minusHours(1)) }
            
            transactionalManager.transaction { tx ->
                eventer.forEach { eventRepository.lagre(Fagsystem.PUNSJ, it, tx) }
            }
            eksternIder.forEach { oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, it.toString())) }
            
            "skal kunne prosessere alle bestillinger" {
                eventRepository.bestillHistorikkvask(Fagsystem.PUNSJ)
                val antallFør = eventRepository.hentAntallHistorikkvaskbestillinger()
                antallFør shouldBe 5
                
                historikkvaskTjeneste.kjørHistorikkvask()
                
                val antallEtter = eventRepository.hentAntallHistorikkvaskbestillinger()
                antallEtter shouldBe 0
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
            : K9PunsjEventDto {
        return K9PunsjEventDto(
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