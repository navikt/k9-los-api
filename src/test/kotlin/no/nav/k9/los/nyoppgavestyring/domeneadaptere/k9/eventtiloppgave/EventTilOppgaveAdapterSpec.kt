package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import org.koin.test.KoinTest
import org.koin.test.get
import java.time.LocalDateTime
import java.util.*

class EventTilOppgaveAdapterSpec : KoinTest, FreeSpec() {
    val eventRepository = get<EventRepository>()
    val transactionalManager = get<TransactionalManager>()
    val oppgaveOppdatertHandler = mockk<OppgaveOppdatertHandler>()

    val oppgaveAdapter = EventTilOppgaveAdapter(
        eventRepository = get(),
        oppgaveV3Tjeneste = get(),
        transactionalManager = get(),
        pepCacheService = get(),
        eventTilOppgaveMapper = get(),
        oppgaveOppdatertHandler = oppgaveOppdatertHandler
    )

    val oppgaveV3Tjeneste = get<OppgaveV3Tjeneste>()
    val oppgaveRepositoryTxWrapper = get<OppgaveRepositoryTxWrapper>()

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

    override suspend fun beforeSpec(spec: Spec) {
        every {
            oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
        } just Runs
    }

    init {
        "En oppgaveevent" - {
            val event = punsjEvent()
            transactionalManager.transaction { tx ->
                eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event), tx)
            }
            "som håndteres av oppgaveadapter" - {
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(null, Fagsystem.PUNSJ, event.eksternId.toString()))
                "skal gi en oppgave i henhold til innsendt event" - {
                    //en eller annen uthenting for full historikk?
                    val internVersjon = transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.hentHøyesteInternVersjon(event.eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                    }
                    internVersjon shouldBe 0
                    "og oppdatertOppgaveHåndterer skal bli kalt" {
                        verify(exactly = 1) {
                            oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
                        }
                    }
                }
            }
        }

        "To oppgaveeventer" - {
            "hvor begge er dirty" - {
                "skal gi to oppgaveversjoner i samme rekkefølge som eventene" {

                }
            }
            "hvor den første er lest inn og den andre er dirty" - {
                "skal bare forsøke å lese inn det andre eventet" - {
                    "skal gi to oppgaveversjoner i samme rekkefølge som eventene" {

                    }
                }

            }
            "hvor den andre er lest inn og den første er dirty" - {
                "skal lese inn dirty event og bestille historikkvask" {

                }
            }
        }
        "Tre oppgaveeventer" - {
            "hvor bare den midterste er dirty og de andre er lest inn" - {
                "skal bare laste inn det midterste eventet" - {
                    "skal bestille historikkvask" {

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
}