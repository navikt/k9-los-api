package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.spyk
import io.mockk.verify
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import org.koin.test.KoinTest
import org.koin.test.get
import java.time.LocalDateTime
import java.util.*

class EventTilOppgaveAdapterSpec : KoinTest, FreeSpec() {
    val transactionalManager = get<TransactionalManager>()

    private lateinit var eventRepository: EventRepository
    private lateinit var oppgaveOppdatertHandler: OppgaveOppdatertHandler
    private lateinit var oppgaveAdapter: EventTilOppgaveAdapter

    val oppgaveV3Tjeneste = get<OppgaveV3Tjeneste>()

    override suspend fun beforeTest(testCase: TestCase) {
        eventRepository = spyk(EventRepository(
            dataSource = get()
        ))
        oppgaveOppdatertHandler = spyk(OppgaveOppdatertHandler(
            oppgaveRepository = get(),
            reservasjonV3Tjeneste = get(),
            eventTilOppgaveMapper = get(),
            pepCacheService = get(),
        ))

        oppgaveAdapter = EventTilOppgaveAdapter(
            eventRepository = eventRepository,
            oppgaveV3Tjeneste = get(),
            transactionalManager = get(),
            eventTilOppgaveMapper = get(),
            oppgaveOppdatertHandler = oppgaveOppdatertHandler
        )

        clearAllMocks()
    }

    init {
        "En oppgaveevent" - {
            val event = punsjEvent()
            transactionalManager.transaction { tx ->
                eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event), tx)
            }
            "som håndteres av oppgaveadapter" - {
                "skal gi en oppgave i henhold til innsendt event" - {
                    "og oppdatertOppgaveHåndterer skal bli kalt" {
                        oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(null, Fagsystem.PUNSJ, event.eksternId.toString()))
                        val internVersjon = transactionalManager.transaction { tx ->
                            oppgaveV3Tjeneste.hentHøyesteInternVersjon(event.eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                        }
                        internVersjon shouldBe 0
                        verify(exactly = 1) {
                            oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
                        }
                        verify(exactly = 0) {
                            eventRepository.bestillHistorikkvask(any(), any(), any())
                        }
                    }
                }
            }
        }

        "To oppgaveeventer" - {
            val eksternId = UUID.randomUUID()
            val event = punsjEvent(eksternId, LocalDateTime.now().minusHours(1))
            val event2 = punsjEvent(eksternId, LocalDateTime.now())
            "hvor begge er dirty" - {
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event), tx)
                    eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event2), tx)
                }
                "skal gi to oppgaveversjoner i samme rekkefølge som eventene" {
                    oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(null, Fagsystem.PUNSJ, eksternId.toString()))
                    verify(exactly = 2) {
                        oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
                    }
                    verify(exactly = 0) {
                        eventRepository.bestillHistorikkvask(any(), any(), any())
                    }
                    val internVersjon = transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.hentHøyesteInternVersjon(event.eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                    }
                    internVersjon shouldBe 1
                }
            }
            "hvor den første er lest inn og den andre er dirty" - {
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event), tx)
                }
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(null, Fagsystem.PUNSJ, eksternId.toString()))
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event2), tx)
                }
                "skal bare forsøke å lese inn det andre eventet" - {
                    "skal gi to oppgaveversjoner i samme rekkefølge som eventene" {
                        oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(null, Fagsystem.PUNSJ, eksternId.toString()))
                        verify(exactly = 1) {
                            oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
                        }
                        verify(exactly = 0) {
                            eventRepository.bestillHistorikkvask(any(), any(), any())
                        }
                        val internVersjon = transactionalManager.transaction { tx ->
                            oppgaveV3Tjeneste.hentHøyesteInternVersjon(event.eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                        }
                        internVersjon shouldBe 1
                    }
                }

            }
            "hvor den andre er lest inn og den første er dirty" - {
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event2), tx)
                }
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(null, Fagsystem.PUNSJ, eksternId.toString()))
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event), tx)
                }
                "skal lese inn dirty event og bestille historikkvask" {
                    oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(null, Fagsystem.PUNSJ, eksternId.toString()))
                    verify(exactly = 1) {
                        oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
                    }
                    verify(exactly = 1) {
                        eventRepository.bestillHistorikkvask(any(), any(), any())
                    }
                    val internVersjon = transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.hentHøyesteInternVersjon(event.eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                    }
                    internVersjon shouldBe 1
                }
            }
        }
        "Tre oppgaveeventer" - {
            val eksternId = UUID.randomUUID()
            val event = punsjEvent(eksternId, LocalDateTime.now().minusHours(2))
            val event2 = punsjEvent(eksternId, LocalDateTime.now().minusHours(1))
            val event3 = punsjEvent(eksternId, LocalDateTime.now())
            "hvor bare den midterste er dirty og de andre er lest inn" - {
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event), tx)
                    eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event3), tx)
                }
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(null, Fagsystem.PUNSJ, eksternId.toString()))
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event2), tx)
                }
                "skal bare laste inn det midterste eventet" - {
                    "skal bestille historikkvask" {
                        oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(null, Fagsystem.PUNSJ, eksternId.toString()))
                        verify(exactly = 1) {
                            oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
                        }
                        verify(exactly = 1) {
                            eventRepository.bestillHistorikkvask(any(), any(), any())
                        }
                        val internVersjon = transactionalManager.transaction { tx ->
                            oppgaveV3Tjeneste.hentHøyesteInternVersjon(event.eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                        }
                        internVersjon shouldBe 2
                    }
                }
            }
        }
        "2 oppgaveeventer" - {
            "hvor den første er dirty og den andre er lest inn".config(enabled = false) - { //feil rekkefølge (kronologisk)
                "og den første har status venter" - {

                    "og den andre har status åpen" - {
                        "og det finnes en reservasjon som er opprettet etter første event" - {
                            "så skal ikke reservasjon annulleres" {

                            }
                        }
                        "og det finnes en reservasjon som er opprettet FØR første event" - {
                            "så skal reservasjonen annulleres" {

                            }
                        }
                        //oppgaven har vært på vent, men er nå åpen. Mottatt dok vi venter på feks

                    }
                }
                "og den første har status åpen" - {
                    "og den andre har status venter" - {
                        //oppgaven har blitt satt på vent av saksbehandler

                    }
                }
            }
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