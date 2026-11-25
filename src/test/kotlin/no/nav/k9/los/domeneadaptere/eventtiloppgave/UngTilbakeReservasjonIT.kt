package no.nav.k9.los.domeneadaptere.eventtiloppgave

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.EventHendelse
import no.nav.k9.los.domeneadaptere.eventmottak.ung.tilbake.UngTilbakeEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.ung.tilbake.UngTilbakeEventHandler
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.AvpOmrådesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.UngOmrådesetup
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.OppgaveRepository
import no.nav.k9.los.reservasjon.ReservasjonV3
import no.nav.k9.los.reservasjon.ReservasjonV3Repository
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import org.koin.test.get
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

class UngTilbakeReservasjonIT : AbstractK9LosIntegrationTest() {
    private val områder = listOf(
        Område(Områder.AKTIVITETSPENGER, FagsakYtelseType.AKTIVITETSPENGER, "AKT"),
        Område(Områder.UNGDOMSPROGRAMYTELSEN, FagsakYtelseType.UNGDOMSYTELSE, "UPY"),
    )
    private val nesteAktørId = AtomicLong(1000000000000L)
    private lateinit var handler: UngTilbakeEventHandler
    private lateinit var eventer: EventRepository
    private lateinit var oppgaver: OppgaveRepository
    private lateinit var reservasjoner: ReservasjonV3Repository
    private lateinit var txManager: TransactionalManager
    private var saksbehandlerId = 0L
    private var beslutterId = 0L

    @BeforeEach
    fun setup() {
        get<AvpOmrådesetup>().setup()
        get<UngOmrådesetup>().setup()
        handler = UngTilbakeEventHandler(get(), get(), get())
        eventer = get()
        oppgaver = get()
        reservasjoner = get()
        txManager = get()
        val saksbehandlere = get<TestSaksbehandlerRepository>()
        fun opprettSaksbehandler(epost: String) = saksbehandlere.opprettSaksbehandler(
            OpprettSaksbehandler(
                navident = null,
                navn = null,
                epost = epost,
                enhet = null,
                områder = områder.map { it.område },
            )
        ).id
        saksbehandlerId = opprettSaksbehandler("saksbehandler@test.no")
        beslutterId = opprettSaksbehandler("beslutter@test.no")
    }

    @TestFactory
    fun `annullerer begge reservasjoner når ingen tilknyttede oppgaver er åpne`() =
        områder.flatMap { område ->
            Oppdatering.entries.map { oppdatering ->
                dynamicTest("${område.område} ${oppdatering.status}") {
                    val aktørId = nesteAktørId.getAndIncrement().toString()
                    val nøkler = reservasjonsnøkler(område, aktørId)
                    val åpen = event(område, aktørId)
                    val ventendeBeslutter = event(område, aktørId).copy(
                        aksjonspunktKoderMedStatusListe = mapOf("7001" to "OPPR", "5005" to "OPPR"),
                    )
                    val uavklart = event(område, aktørId).copy(
                        behandlingStatus = "OPPRE",
                        aksjonspunktKoderMedStatusListe = emptyMap(),
                    )
                    listOf(åpen, ventendeBeslutter, uavklart).forEach(::send)
                    assertOppgave(område, åpen, Oppgavestatus.AAPEN, nøkler.getValue(Rolle.SAKSBEHANDLER))
                    assertOppgave(område, ventendeBeslutter, Oppgavestatus.VENTER, nøkler.getValue(Rolle.BESLUTTER))
                    assertOppgave(område, uavklart, Oppgavestatus.UAVKLART, nøkler.getValue(Rolle.SAKSBEHANDLER))
                    val lagredeReservasjoner = lagReservasjoner(område, nøkler)
                    assertAktiveReservasjoner(område, nøkler, lagredeReservasjoner)

                    val oppdatert = åpen.oppdater(oppdatering)
                    send(oppdatert)

                    assertOppgave(område, oppdatert, oppdatering.status, nøkler.getValue(Rolle.SAKSBEHANDLER))
                    val tilknyttedeOppgaver = txManager.transaction { tx ->
                        oppgaver.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, nøkler.values.toList())
                    }
                    assertEquals(setOf(Oppgavestatus.VENTER, Oppgavestatus.UAVKLART), tilknyttedeOppgaver.map { it.status }.toSet())
                    assertAnnullerteReservasjoner(område, nøkler, lagredeReservasjoner)
                }
            }
        }

    @TestFactory
    fun `beholder begge reservasjoner når en annen tilknyttet oppgave er åpen`() =
        områder.flatMap { område ->
            Oppdatering.entries.flatMap { oppdatering ->
                Rolle.entries.map { rolle ->
                    dynamicTest("${område.område} ${oppdatering.status}, åpen hos $rolle") {
                        val aktørId = nesteAktørId.getAndIncrement().toString()
                        val nøkler = reservasjonsnøkler(område, aktørId)
                        val åpen = event(område, aktørId)
                        val annenÅpen = event(område, aktørId).copy(
                            aksjonspunktKoderMedStatusListe = mapOf(rolle.aksjonspunkt to "OPPR"),
                        )
                        send(åpen)
                        send(annenÅpen)
                        assertOppgave(område, åpen, Oppgavestatus.AAPEN, nøkler.getValue(Rolle.SAKSBEHANDLER))
                        assertOppgave(område, annenÅpen, Oppgavestatus.AAPEN, nøkler.getValue(rolle))
                        val lagredeReservasjoner = lagReservasjoner(område, nøkler)
                        assertAktiveReservasjoner(område, nøkler, lagredeReservasjoner)

                        val oppdatert = åpen.oppdater(oppdatering)
                        send(oppdatert)

                        assertOppgave(område, oppdatert, oppdatering.status, nøkler.getValue(Rolle.SAKSBEHANDLER))
                        assertOppgave(område, annenÅpen, Oppgavestatus.AAPEN, nøkler.getValue(rolle))
                        val åpneOppgaver = txManager.transaction { tx ->
                            oppgaver.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, nøkler.values.toList())
                                .filter { it.status == Oppgavestatus.AAPEN }
                        }
                        assertEquals(listOf(annenÅpen.eksternId.toString()), åpneOppgaver.map { it.eksternId })
                        assertAktiveReservasjoner(område, nøkler, lagredeReservasjoner)
                    }
                }
            }
        }

    private fun event(område: Område, aktørId: String): UngTilbakeEventDto {
        val tid = LocalDateTime.of(2026, 3, 5, 12, 0)
        return UngTilbakeEventDto(
            eksternId = UUID.randomUUID(),
            fagsystem = Fagsystem.UNGTILBAKE.kode,
            saksnummer = "123456",
            aktørId = aktørId,
            eventTid = tid,
            eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
            behandlingStatus = "UTRED",
            ytelseTypeKode = område.ytelse.kode,
            behandlingTypeKode = BehandlingType.TILBAKEKREVING.kode,
            opprettetBehandling = tid.minusDays(1),
            aksjonspunktKoderMedStatusListe = mapOf("5002" to "OPPR"),
        )
    }

    private fun UngTilbakeEventDto.oppdater(oppdatering: Oppdatering) = copy(
        eventTid = eventTid.plusMinutes(1),
        eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
        behandlingStatus = oppdatering.behandlingStatus,
        aksjonspunktKoderMedStatusListe = oppdatering.aksjonspunkter,
    )

    private fun send(event: UngTilbakeEventDto) {
        handler.prosesser(
            event.eksternId.toString(),
            event.eventTid.toString(),
            LosObjectMapper.instance.writeValueAsString(event),
        )
    }

    private fun assertOppgave(område: Område, event: UngTilbakeEventDto, status: Oppgavestatus, nøkkel: String) {
        val oppgave = txManager.transaction { tx ->
            oppgaver.hentNyesteOppgaveForEksternId(tx, område.område, event.eksternId.toString())
        }
        assertEquals(status, oppgave.status)
        assertEquals(nøkkel, oppgave.reservasjonsnøkkel)
        assertEquals(event.eventTid.toString(), oppgave.eksternVersjon)
        assertFalse(eventer.hentAlleEventer(Fagsystem.UNGTILBAKE, event.eksternId.toString()).any { it.dirty })
    }

    private fun reservasjonsnøkler(område: Område, aktørId: String): Map<Rolle, String> {
        val nøkkel = "${område.prefiks}_t_$aktørId"
        return mapOf(Rolle.SAKSBEHANDLER to nøkkel, Rolle.BESLUTTER to "${nøkkel}_beslutter")
    }

    private fun lagReservasjoner(område: Område, nøkler: Map<Rolle, String>): Map<Rolle, ReservasjonV3> {
        val nå = LocalDateTime.now()
        return txManager.transaction { tx ->
            nøkler.mapValues { (rolle, nøkkel) ->
                val reservasjon = reservasjoner.lagreReservasjon(
                    område.område,
                    ReservasjonV3(
                        reservertAv = if (rolle == Rolle.SAKSBEHANDLER) saksbehandlerId else beslutterId,
                        reservasjonsnøkkel = nøkkel,
                        kommentar = "",
                        gyldigFra = nå.minusMinutes(1),
                        gyldigTil = nå.plusDays(1),
                        endretAv = null,
                    ),
                    tx,
                )
                checkNotNull(reservasjon.id)
                reservasjon
            }
        }
    }

    private fun assertAktiveReservasjoner(
        område: Område,
        nøkler: Map<Rolle, String>,
        lagredeReservasjoner: Map<Rolle, ReservasjonV3>,
    ) {
        txManager.transaction { tx ->
            nøkler.forEach { (rolle, nøkkel) ->
                val reservasjon = reservasjoner.hentAktivReservasjonForReservasjonsnøkkel(område.område, nøkkel, tx)
                val lagret = lagredeReservasjoner.getValue(rolle)
                assertEquals(lagret.id, reservasjon?.id, "$rolle")
                assertEquals(lagret, reservasjon, "$rolle")
                assertEquals(lagret.kommentar, reservasjon?.kommentar, "$rolle")
            }
        }
    }

    private fun assertAnnullerteReservasjoner(
        område: Område,
        nøkler: Map<Rolle, String>,
        lagredeReservasjoner: Map<Rolle, ReservasjonV3>,
    ) {
        txManager.transaction { tx ->
            nøkler.forEach { (rolle, nøkkel) ->
                assertNull(reservasjoner.hentAktivReservasjonForReservasjonsnøkkel(område.område, nøkkel, tx), "$rolle")
                val historikk = reservasjoner.hentReservasjonTidslinjeMedEndringer(område.område, nøkkel, tx).single()
                assertEquals(lagredeReservasjoner.getValue(rolle).id, historikk.id)
                assertTrue(historikk.annullertFørUtløp)
                assertEquals(historikk.id, historikk.annullertReservasjonId)
                assertNotNull(historikk.endringId)
                assertNull(historikk.nyReservasjonId)
            }
        }
    }

    private data class Område(val område: Områder, val ytelse: FagsakYtelseType, val prefiks: String)

    private enum class Rolle(val aksjonspunkt: String) {
        SAKSBEHANDLER("5002"),
        BESLUTTER("5005"),
    }

    private enum class Oppdatering(
        val status: Oppgavestatus,
        val behandlingStatus: String,
        val aksjonspunkter: Map<String, String>,
    ) {
        VENTER(Oppgavestatus.VENTER, "UTRED", mapOf("7001" to "OPPR")),
        LUKKET(Oppgavestatus.LUKKET, "AVSLU", emptyMap()),
    }
}
