package no.nav.k9.los.domeneadaptere.eventtiloppgave

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNull
import assertk.assertions.isTrue
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventlager.EventLagret
import no.nav.k9.los.domeneadaptere.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.kodeverk.AktOppgavetypenavn
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
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
import no.nav.ung.kodeverk.Fagsystem as UngFagsystem
import no.nav.k9.los.domeneadaptere.eventtiloppgave.akt.Områdesetup as AktOmrådesetup

class AktivitetspengerDel1Del2EventserieTest : AbstractK9LosIntegrationTest() {

    private val DEL1 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL1.kode
    private val DEL2 = AktOppgavetypenavn.AKTIVITETSPENGERORDINÆRDEL2.kode

    private val eksternId = UUID.randomUUID()
    private val opprettetBehandling = LocalDateTime.of(2026, 3, 1, 8, 0, 0)

    // e1: del1, e2: del1, e3: del2, e4: del1, e5: del2, e6: del2
    private val eventserie: List<UngSakEventDto> = listOf(
        lagEvent(1, lokalkontor = true),
        lagEvent(2, lokalkontor = true),
        lagEvent(3, lokalkontor = false),
        lagEvent(4, lokalkontor = true),
        lagEvent(5, lokalkontor = false),
        lagEvent(6, lokalkontor = false),
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
            Triple(0, DEL1, eksternVersjoner[0]),
            Triple(1, DEL1, eksternVersjoner[1]),
            Triple(0, DEL2, eksternVersjoner[2]),
            Triple(2, DEL1, eksternVersjoner[3]),
            Triple(1, DEL2, eksternVersjoner[4]),
            Triple(2, DEL2, eksternVersjoner[5]),
        )

        assertThat(VaskeeventSerieutleder.nummererEventserie(eventer).tilTriple())
            .containsExactly(*forventet.toTypedArray())
        assertThat(VaskeeventSerieutleder.nummererEventseriePerOppgavetype(eventer).tilTriple())
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
        lagre(eventserie.subList(0, 3))
        adapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.UNGSAK, eksternId.toString()))

        lagre(eventserie.subList(3, 6))
        adapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.UNGSAK, eksternId.toString()))

        assertOppgaver()
    }

    private fun assertOppgaver() {
        assertVersjonskjede(DEL1, listOf(eksternVersjoner[0], eksternVersjoner[1], eksternVersjoner[3]), "del1")
        assertVersjonskjede(DEL2, listOf(eksternVersjoner[2], eksternVersjoner[4], eksternVersjoner[5]), "del2")
        assertThat(eventRepository.hentAlleEventer(Fagsystem.UNGSAK, eksternId.toString()).none { it.dirty }).isTrue()
    }

    private fun assertVersjonskjede(oppgavetype: String, forventedeEksternVersjoner: List<String>, del: String) {
        transactionalManager.transaction { tx ->
            assertThat(
                oppgaveV3Tjeneste.hentHøyesteInternVersjon(eksternId.toString(), oppgavetype, Områder.AKTIVITETSPENGER, tx)
            ).isEqualTo(forventedeEksternVersjoner.lastIndex)

            forventedeEksternVersjoner.forEachIndexed { internVersjon, eksternVersjon ->
                val oppgave = oppgaveV3Tjeneste.hentOppgaveversjon(
                    Områder.AKTIVITETSPENGER, oppgavetype, eksternId.toString(), internVersjon, tx
                )!!
                assertThat(oppgave.oppgavetype.eksternId).isEqualTo(oppgavetype)
                assertThat(oppgave.eksternVersjon).isEqualTo(eksternVersjon)
                assertThat(oppgave.reservasjonsnøkkel).isEqualTo("AKT_b_${del}_1234567890123")
                if (internVersjon == forventedeEksternVersjoner.lastIndex) {
                    assertThat(oppgave.aktiv).isTrue()
                } else {
                    assertThat(oppgave.aktiv).isFalse()
                }
            }

            assertThat(
                oppgaveV3Tjeneste.hentOppgaveversjon(
                    Områder.AKTIVITETSPENGER, oppgavetype, eksternId.toString(), forventedeEksternVersjoner.size, tx
                )
            ).isNull()

            val aktiv = oppgaveV3Tjeneste.hentAktivOppgave(eksternId.toString(), oppgavetype, Områder.AKTIVITETSPENGER, tx)
            assertThat(aktiv.eksternVersjon).isEqualTo(forventedeEksternVersjoner.last())
        }
    }

    private fun List<Pair<Int, EventLagret>>.tilTriple() =
        map { (nummer, event) -> Triple(nummer, event.oppgavetypeKode(), event.eksternVersjon) }

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

    private fun lagEvent(nummer: Long, lokalkontor: Boolean): UngSakEventDto {
        val aksjonspunkter = if (lokalkontor) {
            listOf(ap(AksjonspunktDefinisjon.LOKALKONTOR_FORESLÅR_VILKÅR, AksjonspunktStatus.OPPRETTET))
        } else {
            listOf(
                ap(AksjonspunktDefinisjon.LOKALKONTOR_FORESLÅR_VILKÅR, AksjonspunktStatus.UTFØRT),
                ap(AksjonspunktDefinisjon.KONTROLLER_INNTEKT, AksjonspunktStatus.OPPRETTET),
            )
        }
        val åpentAksjonspunkt = if (lokalkontor) AksjonspunktDefinisjon.LOKALKONTOR_FORESLÅR_VILKÅR else AksjonspunktDefinisjon.KONTROLLER_INNTEKT
        return UngSakEventDto(
            eksternId = eksternId,
            fagsystem = UngFagsystem.UNG_SAK,
            saksnummer = "AKT123456",
            aktørId = "1234567890123",
            eventTid = opprettetBehandling.plusHours(nummer),
            eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
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

