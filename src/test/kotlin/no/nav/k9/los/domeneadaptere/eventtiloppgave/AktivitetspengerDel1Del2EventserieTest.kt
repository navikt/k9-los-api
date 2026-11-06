package no.nav.k9.los.domeneadaptere.eventtiloppgave

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.kodeverk.AktOppgavetypenavn
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.Områdesetup as AktOmrådesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.Områdesetup as UpyOmrådesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.kodeverk.UpyOppgavetypenavn
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavemottak.OppgaveV3Tjeneste
import no.nav.ung.kodeverk.behandling.BehandlingResultatType
import no.nav.ung.kodeverk.behandling.BehandlingStatus
import no.nav.ung.kodeverk.behandling.BehandlingType
import no.nav.ung.kodeverk.behandling.FagsakYtelseType
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.ung.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.ung.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.ung.kodeverk.hendelse.EventHendelse
import no.nav.ung.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import no.nav.ung.sak.typer.Periode
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlin.reflect.KClass
import no.nav.ung.kodeverk.Fagsystem as UngFagsystem

class AktivitetspengerDel1Del2EventserieTest : AbstractK9LosIntegrationTest() {

    private val DEL1 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1.kode
    private val DEL2 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2.kode

    private val eksternId = UUID.randomUUID()
    private val opprettetBehandling = LocalDateTime.of(2026, 3, 1, 8, 0, 0)

    // e1: del1, e2: del1, e3: lukk del1, e4: del2, e5: lukk del2, e6: del1, e7: lukk del1, e8: del2, e9: del2
    private val eventserie: List<UngSakEventDto> = listOf(
        lagEvent(1, lokalkontor = true),
        lagEvent(2, lokalkontor = true),
        lagEvent(3, lokalkontor = false, hendelse = EventHendelse.AVSLUTTE_NAVKONTOR),
        lagEvent(4, lokalkontor = false),
        lagEvent(5, lokalkontor = true, hendelse = EventHendelse.GJENOPPTA_NAVKONTOR),
        lagEvent(6, lokalkontor = true),
        lagEvent(7, lokalkontor = false, hendelse = EventHendelse.AVSLUTTE_NAVKONTOR),
        lagEvent(8, lokalkontor = false),
        lagEvent(9, lokalkontor = false),
    )
    private val eksternVersjoner = eventserie.map { it.eventTid.toString() }

    private lateinit var eventRepository: EventRepository
    private lateinit var transactionalManager: TransactionalManager
    private lateinit var oppgaveV3Tjeneste: OppgaveV3Tjeneste
    private lateinit var adapter: EventTilOppgaveAdapter

    @BeforeEach
    fun setup() {
        get<AktOmrådesetup>().setup()
        eventRepository = get()
        transactionalManager = get()
        oppgaveV3Tjeneste = get()
        adapter = get()
    }

    @Test
    fun `vaskeeventserieutleder nummererer eventserien per oppgavetype`() {
        lagre(eventserie)
        val eventer = eventRepository.hentAlleEventer(Fagsystem.UNGSAK, eksternId.toString())

        val forventet = listOf(
            Forventet(NummerertEventHandling.MapEventHandling::class, 0, DEL1, eksternVersjoner[0]),
            Forventet(NummerertEventHandling.MapEventHandling::class, 1, DEL1, eksternVersjoner[1]),
            Forventet(NummerertEventHandling.MapEventHandling::class, 2, DEL1, eksternVersjoner[2]),
            Forventet(NummerertEventHandling.MapEventHandling::class, 0, DEL2, eksternVersjoner[3]),
            Forventet(NummerertEventHandling.MapEventHandling::class, 1, DEL2, eksternVersjoner[4]),
            Forventet(NummerertEventHandling.MapEventHandling::class, 3, DEL1, eksternVersjoner[5]),
            Forventet(NummerertEventHandling.MapEventHandling::class, 4, DEL1, eksternVersjoner[6]),
            Forventet(NummerertEventHandling.MapEventHandling::class, 2, DEL2, eksternVersjoner[7]),
            Forventet(NummerertEventHandling.MapEventHandling::class, 3, DEL2, eksternVersjoner[8]),
        )

        assertThat(utledEventhandlinger(eventer).tilForventet())
            .containsExactly(*forventet.toTypedArray())
        assertThat(utledEventhandlinger(eventer).tilForventet())
            .containsExactly(*forventet.toTypedArray())
    }

    @Test
    fun `eventtiloppgaveadapter lager separate versjonskjeder for del1 og del2 i én kjøring`() {
        lagre(eventserie)

        adapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.UNGSAK, eksternId.toString()))

        assertOppgaver()
    }

    @Test
    fun `eventtiloppgaveadapter lager separate versjonskjeder for del1 og del2 over flere kjøringer`() {
        lagre(eventserie.subList(0, 4))
        adapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.UNGSAK, eksternId.toString()))

        lagre(eventserie.subList(4, 9))
        adapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.UNGSAK, eksternId.toString()))

        assertOppgaver()
    }

    @Test
    fun `eventtiloppgaveadapter lager oppgave for ungdomsytelse`() {
        get<UpyOmrådesetup>().setup()
        val event = lagEvent(1, lokalkontor = true).copy(ytelseTypeKode = FagsakYtelseType.UNGDOMSYTELSE.kode)
        lagre(listOf(event))

        val statistikkteller = adapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.UNGSAK, eksternId.toString()))

        assertThat(statistikkteller).isEqualTo(1L)
        assertThat(eventRepository.hentAlleEventer(Fagsystem.UNGSAK, eksternId.toString()).single().dirty).isFalse()
        transactionalManager.transaction { tx ->
            val oppgave = oppgaveV3Tjeneste.hentAktivOppgave(
                eksternId.toString(),
                UpyOppgavetypenavn.UNGDOMSPROGRAMYTELSENORDINÆR.kode,
                Områder.UNGDOMSPROGRAMYTELSEN,
                tx
            )
            assertThat(oppgave.eksternVersjon).isEqualTo(event.eventTid.toString())
            assertThat(oppgave.status).isEqualTo(Oppgavestatus.AAPEN)
            assertThat(oppgave.reservasjonsnøkkel).isEqualTo("UPY_b_del1_1234567890123")
        }
    }

    private fun assertOppgaver() {
        assertVersjonskjede(
            DEL1,
            listOf(
                eksternVersjoner[0] to Oppgavestatus.AAPEN,
                eksternVersjoner[1] to Oppgavestatus.AAPEN,
                eksternVersjoner[2] to Oppgavestatus.LUKKET,
                eksternVersjoner[5] to Oppgavestatus.AAPEN,
                eksternVersjoner[6] to Oppgavestatus.LUKKET,
            ),
            "del1"
        )
        assertVersjonskjede(
            DEL2,
            listOf(
                eksternVersjoner[3] to Oppgavestatus.AAPEN,
                eksternVersjoner[4] to Oppgavestatus.LUKKET,
                eksternVersjoner[7] to Oppgavestatus.AAPEN,
                eksternVersjoner[8] to Oppgavestatus.AAPEN,
            ),
            "del2"
        )
        assertThat(eventRepository.hentAlleEventer(Fagsystem.UNGSAK, eksternId.toString()).none { it.dirty }).isTrue()
    }

    private fun assertVersjonskjede(oppgavetype: String, forventedeVersjoner: List<Pair<String, Oppgavestatus>>, del: String) {
        transactionalManager.transaction { tx ->
            assertThat(
                oppgaveV3Tjeneste.hentHøyesteInternVersjon(eksternId.toString(), oppgavetype, Områder.AKTIVITETSPENGER, tx)
            ).isEqualTo(forventedeVersjoner.lastIndex)

            forventedeVersjoner.forEachIndexed { internVersjon, (eksternVersjon, status) ->
                val oppgave = oppgaveV3Tjeneste.hentOppgaveversjon(
                    Områder.AKTIVITETSPENGER, oppgavetype, eksternId.toString(), internVersjon, tx
                )!!
                assertThat(oppgave.oppgavetype.eksternId).isEqualTo(oppgavetype)
                assertThat(oppgave.eksternVersjon).isEqualTo(eksternVersjon)
                assertThat(oppgave.status).isEqualTo(status)
                assertThat(oppgave.reservasjonsnøkkel).isEqualTo("AKT_b_${del}_1234567890123")
                if (internVersjon == forventedeVersjoner.lastIndex) {
                    assertThat(oppgave.aktiv).isTrue()
                } else {
                    assertThat(oppgave.aktiv).isFalse()
                }
            }

            assertThat(
                oppgaveV3Tjeneste.hentOppgaveversjon(
                    Områder.AKTIVITETSPENGER, oppgavetype, eksternId.toString(), forventedeVersjoner.size, tx
                )
            ).isNull()

            val aktiv = oppgaveV3Tjeneste.hentAktivOppgave(eksternId.toString(), oppgavetype, Områder.AKTIVITETSPENGER, tx)
            assertThat(aktiv.eksternVersjon).isEqualTo(forventedeVersjoner.last().first)
        }
    }

    private data class Forventet(val steg: KClass<out NummerertEventHandling>, val eventnummer: Int, val oppgavetype: String, val eksternVersjon: String)

    private fun List<NummerertEventHandling>.tilForventet() =
        map { Forventet(it::class, it.eventnummer, it.oppgavetype.kode, it.event.eksternVersjon) }

    private fun lagre(eventer: List<UngSakEventDto>) {
        transactionalManager.transaction { tx ->
            eventer.forEach { event ->
                eventRepository.lagre(
                    Fagsystem.UNGSAK,
                    event.eksternId.toString(),
                    event.eventTid.toString(),
                    LosObjectMapper.instance.writeValueAsString(event),
                    tx
                )
            }
        }
    }

    private fun lagEvent(
        nummer: Long,
        lokalkontor: Boolean,
        hendelse: EventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
    ): UngSakEventDto {
        val aksjonspunkter = if (lokalkontor) {
            listOf(ap(AksjonspunktDefinisjon.NAV_KONTOR_FORESLÅR_VILKÅR, AksjonspunktStatus.OPPRETTET))
        } else {
            listOf(
                ap(AksjonspunktDefinisjon.NAV_KONTOR_FORESLÅR_VILKÅR, AksjonspunktStatus.UTFØRT),
                ap(AksjonspunktDefinisjon.KONTROLLER_INNTEKT, AksjonspunktStatus.OPPRETTET),
            )
        }
        val åpentAksjonspunkt = if (lokalkontor) AksjonspunktDefinisjon.NAV_KONTOR_FORESLÅR_VILKÅR else AksjonspunktDefinisjon.KONTROLLER_INNTEKT
        return UngSakEventDto(
            eksternId = eksternId,
            fagsystem = UngFagsystem.UNG_SAK,
            saksnummer = "AKT123456",
            aktørId = "1234567890123",
            eventTid = opprettetBehandling.plusHours(nummer),
            eventHendelse = hendelse,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = åpentAksjonspunkt.behandlingSteg.kode,
            behandlendeEnhet = "4416",
            ansvarligBeslutterForTotrinn = null,
            ansvarligSaksbehandlerForTotrinn = null,
            navKontorAnsvarligSaksbehandler = if (lokalkontor) "Z123456" else null,
            navKontorBeslutter = null,
            resultatType = BehandlingResultatType.IKKE_FASTSATT.kode,
            ytelseTypeKode = FagsakYtelseType.AKTIVITETSPENGER.kode,
            behandlingTypeKode = BehandlingType.FØRSTEGANGSSØKNAD.kode,
            eldsteDatoMedEndringFraSøker = opprettetBehandling,
            opprettetBehandling = opprettetBehandling,
            fagsakPeriode = Periode(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)),
            aksjonspunktTilstander = aksjonspunkter,
            nyeKrav = false,
            vedtaksdato = null,
            behandlingstidFrist = LocalDate.of(2026, 4, 1),
            behandlingsårsaker = emptyList(),
        )
    }

    private fun ap(aksjonspunkt: AksjonspunktDefinisjon, status: AksjonspunktStatus) = AksjonspunktTilstandDto(
        aksjonspunkt.kode, status, Venteårsak.UDEFINERT, null, null, opprettetBehandling, opprettetBehandling,
    )
}
