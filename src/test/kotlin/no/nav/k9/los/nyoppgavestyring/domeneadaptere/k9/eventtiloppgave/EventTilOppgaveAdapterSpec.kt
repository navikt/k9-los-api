package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave

import io.kotest.core.spec.style.FreeSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.spyk
import io.mockk.verify
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.K9Oppgavetypenavn
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.HistorikkvaskBestilling
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.oppgavemottak.OppgaveV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.QueryRequest
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.oppgaveuthenting.query.mapping.EksternFeltverdiOperator
import no.nav.k9.sak.typer.AktørId
import no.nav.k9.sak.typer.JournalpostId
import org.koin.core.qualifier.named
import org.koin.test.KoinTest
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class EventTilOppgaveAdapterSpec : KoinTest, FreeSpec() {
    val transactionalManager = get<TransactionalManager>()

    private lateinit var eventRepository: EventRepository
    private lateinit var oppgaveOppdatertHandler: OppgaveOppdatertHandler
    private lateinit var oppgaveAdapter: EventTilOppgaveAdapter
    private lateinit var oppgaveQueryService: OppgaveQueryService
    private val historikkvaskTjeneste = get<HistorikkvaskTjeneste>()

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
            køpåvirkendeHendelseChannel = get(named("KøpåvirkendeHendelseChannel")),
        ))

        oppgaveAdapter = EventTilOppgaveAdapter(
            eventRepository = eventRepository,
            oppgaveV3Tjeneste = get(),
            transactionalManager = get(),
            eventTilOppgaveMapper = get(),
            oppgaveOppdatertHandler = oppgaveOppdatertHandler,
            vaskeeventSerieutleder = get(),
            ajourholdTjeneste = get(),
            statistikkRepository = get(),
        )

        oppgaveQueryService = get()

        clearAllMocks()
    }

    init {
        "En oppgaveevent" - {
            val event = punsjEvent()
            transactionalManager.transaction { tx ->
                eventRepository.lagre(Fagsystem.PUNSJ, event, tx)
            }
            "som håndteres av oppgaveadapter" - {
                "skal gi en oppgave i henhold til innsendt event" - {
                    "og oppdatertOppgaveHåndterer skal bli kalt" {
                        oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, event.eksternId.toString()))
                        val internVersjon = transactionalManager.transaction { tx ->
                            oppgaveV3Tjeneste.hentHøyesteInternVersjon(event.eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                        }
                        internVersjon shouldBe 0
                        verify(exactly = 1) {
                            oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
                        }
                        verify(exactly = 0) {
                            eventRepository.bestillHistorikkvask(any<Fagsystem>(), any<String>(), any<TransactionalSession>())
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
                    eventRepository.lagre(Fagsystem.PUNSJ, event, tx)
                    eventRepository.lagre(Fagsystem.PUNSJ, event2, tx)
                }
                "skal gi to oppgaveversjoner i samme rekkefølge som eventene" {
                    oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()))
                    verify(exactly = 2) {
                        oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
                    }
                    verify(exactly = 0) {
                        eventRepository.bestillHistorikkvask(any<Fagsystem>(), any<String>(), any<TransactionalSession>())
                    }
                    val internVersjon = transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.hentHøyesteInternVersjon(event.eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                    }
                    internVersjon shouldBe 1
                }
            }
            "hvor den første er lest inn og den andre er dirty" - {
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, event, tx)
                }
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()))
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, event2, tx)
                }
                "skal bare forsøke å lese inn det andre eventet" - {
                    "skal gi to oppgaveversjoner i samme rekkefølge som eventene" {
                        oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()))
                        verify(exactly = 1) {
                            oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
                        }
                        verify(exactly = 0) {
                            eventRepository.bestillHistorikkvask(any<Fagsystem>(), any<String>(), any<TransactionalSession>())
                        }
                        val internVersjon = transactionalManager.transaction { tx ->
                            oppgaveV3Tjeneste.hentHøyesteInternVersjon(event.eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                        }
                        internVersjon shouldBe 1
                    }
                }
            }
            "hvor den første har ytelsestype og den andre mangler ytelsestype" - {
                val event2m = event2.copy(ytelse = null)
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, event, tx)
                }
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()))
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, event2m, tx)
                }
                "skal fylle ut ytelsestype med foregående oppgaveversjon sin ytelsestype" {
                    oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()))
                    verify(exactly = 1) {
                        oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
                    }
                    verify(exactly = 0) {
                        eventRepository.bestillHistorikkvask(any<Fagsystem>(), any<String>(), any<TransactionalSession>())
                    }
                    val internVersjon = transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.hentHøyesteInternVersjon(event.eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                    }
                    internVersjon shouldBe 1
                    val aktivOppgave = transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.hentAktivOppgave(event.eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                    }

                    aktivOppgave.hentVerdi("ytelsestype") shouldBe "ytelse"
                }
            }
            "hvor den andre er lest inn og den første er dirty" - {
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, event2, tx)
                }
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()))
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, event, tx)
                }
                "skal lese inn dirty event og bestille historikkvask" {
                    oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()))
                    verify(exactly = 1) {
                        oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
                    }
                    verify(exactly = 1) {
                        eventRepository.bestillHistorikkvask(any<Fagsystem>(), any<String>(), any<TransactionalSession>())
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
                    eventRepository.lagre(Fagsystem.PUNSJ, event, tx)
                    eventRepository.lagre(Fagsystem.PUNSJ, event3, tx)
                }
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()))
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.PUNSJ, event2, tx)
                }
                "skal bare laste inn det midterste eventet" - {
                    "skal bestille historikkvask" {
                        oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()))
                        verify(exactly = 1) {
                            oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
                        }
                        verify(exactly = 1) {
                            eventRepository.bestillHistorikkvask(any<Fagsystem>(), any<String>(), any<TransactionalSession>())
                        }
                        val internVersjon = transactionalManager.transaction { tx ->
                            oppgaveV3Tjeneste.hentHøyesteInternVersjon(event.eksternId.toString(), K9Oppgavetypenavn.PUNSJ.kode, "K9", tx)
                        }
                        internVersjon shouldBe 2
                    }
                }
            }
        }
        "Vaskeeventer" - {
            val eksternId = UUID.randomUUID()
            "Sendt inn som første event for en eksternId" - {
                val event = k9SakEvent(eksternId, LocalDateTime.now().minusHours(1), EventHendelse.VASKEEVENT, 0)
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.K9SAK, event, tx)
                }
                "skal opprette oppgaven i henhhold til innsendt event" {
                    oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.K9SAK, eksternId.toString()))
                    transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.hentHøyesteInternVersjon(eksternId.toString(), K9Oppgavetypenavn.SAK.kode, "K9", tx) shouldBe 0
                    }
                }
            }
            "Sendt inn etter en ordinær oppdatering" - {
                val ordinærevent =k9SakEvent(eksternId, LocalDateTime.now().minusHours(1), EventHendelse.BEHANDLINGSKONTROLL_EVENT)
                val vaskeevent = k9SakEvent(eksternId, LocalDateTime.now().minusHours(0), EventHendelse.VASKEEVENT, 99)
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.K9SAK, ordinærevent, tx)
                    eventRepository.lagre(Fagsystem.K9SAK, vaskeevent, tx)
                }
                "Skal overskrive den ordinære oppdateringen" {
                    oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.K9SAK, eksternId.toString()))
                    transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.hentHøyesteInternVersjon(eksternId.toString(), K9Oppgavetypenavn.SAK.kode, "K9", tx) shouldBe 0
                        oppgaveV3Tjeneste.hentAktivOppgave(eksternId.toString(), K9Oppgavetypenavn.SAK.kode, "K9", tx).hentVerdi("saksnummer") shouldBe "99"
                    }
                }
            }
            "Sendt inn etter to ordinære oppdateringer, hvor den første har vært lagret tidligere" - {
                val ordinærevent1 = k9SakEvent(eksternId, LocalDateTime.now().minusHours(2), EventHendelse.BEHANDLINGSKONTROLL_EVENT)
                val ordinærevent2 = k9SakEvent(eksternId, LocalDateTime.now().minusHours(1), EventHendelse.BEHANDLINGSKONTROLL_EVENT)
                val vaskeevent = k9SakEvent(eksternId, LocalDateTime.now().minusHours(0), EventHendelse.VASKEEVENT, 99)
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.K9SAK, ordinærevent1, tx)
                }
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.K9SAK, eksternId.toString()))
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.K9SAK, ordinærevent2, tx)
                    eventRepository.lagre(Fagsystem.K9SAK, vaskeevent, tx)
                }
                "Skal overskrive den siste ordinære oppdateringen" {
                    oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.K9SAK, eksternId.toString()))
                    transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.hentHøyesteInternVersjon(eksternId.toString(), K9Oppgavetypenavn.SAK.kode, "K9", tx) shouldBe 1
                        oppgaveV3Tjeneste.hentAktivOppgave(eksternId.toString(), K9Oppgavetypenavn.SAK.kode, "K9", tx).hentVerdi("saksnummer") shouldBe "99"
                    }
                }
            }
            "Sendt inn mellom to ordinære oppdateringer" - {
                val ordinærevent1 = k9SakEvent(eksternId, LocalDateTime.now().minusHours(2), EventHendelse.BEHANDLINGSKONTROLL_EVENT)
                val vaskeevent = k9SakEvent(eksternId, LocalDateTime.now().minusHours(1), EventHendelse.VASKEEVENT, 99)
                val ordinærevent2 = k9SakEvent(eksternId, LocalDateTime.now().minusHours(0), EventHendelse.BEHANDLINGSKONTROLL_EVENT)
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.K9SAK, ordinærevent1, tx)
                    eventRepository.lagre(Fagsystem.K9SAK, vaskeevent, tx)
                    eventRepository.lagre(Fagsystem.K9SAK, ordinærevent2, tx)
                }
                "Skal overskrive den første ordinære oppdateringen" {
                    oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.K9SAK, eksternId.toString()))
                    transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.hentHøyesteInternVersjon(eksternId.toString(), K9Oppgavetypenavn.SAK.kode, "K9", tx) shouldBe 1
                        oppgaveV3Tjeneste.hentAktivOppgave(eksternId.toString(), K9Oppgavetypenavn.SAK.kode, "K9", tx).hentVerdi("saksnummer") shouldBe "624QM"
                        oppgaveV3Tjeneste.hentOppgaveversjon("K9", K9Oppgavetypenavn.SAK.kode, eksternId.toString(), 0, tx)!!.hentVerdi("saksnummer") shouldBe "99"
                    }
                }
            }
            "sendt i to forskjellige hendelser etter en ordinær oppdatering" - {
                val ordinærevent1 = k9SakEvent(eksternId, LocalDateTime.now().minusHours(2), EventHendelse.BEHANDLINGSKONTROLL_EVENT)
                val vaskeevent1 = k9SakEvent(eksternId, LocalDateTime.now().minusHours(1), EventHendelse.VASKEEVENT, 75)
                val vaskeevent2 = k9SakEvent(eksternId, LocalDateTime.now().minusHours(0), EventHendelse.VASKEEVENT, 76)
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.K9SAK, ordinærevent1, tx)
                    eventRepository.lagre(Fagsystem.K9SAK, vaskeevent1, tx)
                }
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.K9SAK, eksternId.toString()))
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.K9SAK, vaskeevent2, tx)
                }
                "Skal overskrive den ordinære oppdateringen begge ganger" {
                    oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.K9SAK, eksternId.toString()))
                    transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.hentHøyesteInternVersjon(eksternId.toString(), K9Oppgavetypenavn.SAK.kode, "K9", tx) shouldBe 0
                        oppgaveV3Tjeneste.hentAktivOppgave(eksternId.toString(), K9Oppgavetypenavn.SAK.kode, "K9", tx).hentVerdi("saksnummer") shouldBe "76"
                        oppgaveQueryService.queryForAntall(QueryRequest( //for å sjekke innhold i oppgave_v3_part
                            OppgaveQuery(
                                listOf(
                                    FeltverdiOppgavefilter(
                                        "K9",
                                        "saksnummer",
                                        EksternFeltverdiOperator.EQUALS,
                                        listOf("76")
                                    )
                                )
                            )
                        )) shouldBe 1
                    }
                }
            }
        }

        "Historikkvask-kontekst" - {
            val eksternId = UUID.randomUUID()
            val event1 = punsjEvent(eksternId, LocalDateTime.now().minusHours(2))
            val event2 = punsjEvent(eksternId, LocalDateTime.now().minusHours(1))
            val event3 = punsjEvent(eksternId, LocalDateTime.now())
            transactionalManager.transaction { tx ->
                eventRepository.lagre(Fagsystem.PUNSJ, event1, tx)
                eventRepository.lagre(Fagsystem.PUNSJ, event2, tx)
                eventRepository.lagre(Fagsystem.PUNSJ, event3, tx)
            }
            "med tre eventer" - {
                "oppgaveOppdatertHandler skal ikke kalles under historikkvask" {
                    transactionalManager.transaction { tx ->
                        oppgaveAdapter.oppdaterOppgaveForEksternIdUnderHistorikkvask(
                            EventNøkkel(Fagsystem.PUNSJ, eksternId.toString()), tx
                        )
                    }
                    verify(exactly = 0) {
                        oppgaveOppdatertHandler.håndterOppgaveOppdatert(any(), any(), any())
                    }
                    verify(exactly = 0) {
                        oppgaveOppdatertHandler.oppdaterPepCache(any(), any())
                    }
                }
            }

            "allerede sendt k9sak-versjon" - {
                val eksternId = UUID.randomUUID()
                val event = k9SakEvent(
                    eksternId = eksternId,
                    eventTid = LocalDateTime.now().minusHours(1),
                    eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
                )
                transactionalManager.transaction { tx ->
                    eventRepository.lagre(Fagsystem.K9SAK, event, tx)
                }
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.K9SAK, eksternId.toString()))

                "skal ikke legges tilbake i dvh-pending av historikkvask" {
                    val params = mapOf(
                        "eksternId" to eksternId.toString(),
                        "eksternVersjon" to event.eventTid.toString(),
                    )
                    transactionalManager.transaction { tx ->
                        tx.run(
                            queryOf(
                                """
                                delete from oppgave_v3_dvh_pending
                                where ekstern_id = :eksternId and ekstern_versjon = :eksternVersjon
                                """.trimIndent(),
                                params,
                            ).asUpdate
                        )
                    }

                    hentPendingAntall(eksternId.toString()) shouldBe 0L

                    historikkvaskTjeneste.vaskBestilling(
                        HistorikkvaskBestilling(
                            eventlagerNøkkel = null,
                            eksternId = eksternId.toString(),
                            fagsystem = Fagsystem.K9SAK,
                        )
                    )

                    hentPendingAntall(eksternId.toString()) shouldBe 0L
                }
            }
        }

    }

    private fun k9SakEvent(eksternId: UUID = UUID.randomUUID(), eventTid: LocalDateTime = LocalDateTime.now(), eventHendelse: EventHendelse, saksnummerSomTeller: Int? = null,) : K9SakEventDto {
        return K9SakEventDto(
            eksternId = eksternId,
            fagsystem = Fagsystem.K9SAK,
            saksnummer = saksnummerSomTeller?.let { saksnummerSomTeller.toString() } ?: "624QM",
            aktørId = "1442456610368",
            vedtaksdato = null,
            behandlingId = 1050437,
            behandlingstidFrist = LocalDate.now().plusDays(1),
            eventTid = eventTid,
            eventHendelse = eventHendelse,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.kode,
            ytelseTypeKode = FagsakYtelseType.PLEIEPENGER_SYKT_BARN.toString(),
            behandlingTypeKode = BehandlingType.FORSTEGANGSSOKNAD.kode,
            opprettetBehandling = LocalDateTime.now(),
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = emptyMap<String, String>().toMutableMap(),
            aksjonspunktTilstander = emptyList(),
            merknader = emptyList()
        )
    }

    fun punsjEvent(eksternId: UUID = UUID.randomUUID(),
                   eksternVersjon: LocalDateTime = LocalDateTime.now().minusHours(1),
                   tellerForYtelse: Int? = null)
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
            ytelse = tellerForYtelse?.let { tellerForYtelse.toString() } ?: "ytelse",
            sendtInn = false,
            ferdigstiltAv = "saksbehandler",
            journalførtTidspunkt = LocalDateTime.now().minusDays(1),
        )
    }

    private fun hentPendingAntall(eksternId: String): Long {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                    select count(*) as antall
                    from oppgave_v3_dvh_pending
                    where ekstern_id = :eksternId
                    """.trimIndent(),
                    mapOf("eksternId" to eksternId),
                ).map { row -> row.long("antall") }.asSingle
            ) ?: 0L
        }
    }
}