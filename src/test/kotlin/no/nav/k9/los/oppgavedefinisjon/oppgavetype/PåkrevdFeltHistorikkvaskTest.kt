package no.nav.k9.los.oppgavedefinisjon.oppgavetype

import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.AvpOmrådesetup
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.kodeverk.AvpBehandlendeEnhet
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon.AktivitetspengerFelter
import no.nav.k9.los.domeneadaptere.eventtiloppgave.avp.oppgavedefinisjon.AktivitetspengerOppgavetyper
import no.nav.k9.los.domeneadaptere.statistikk.StatistikkRepository
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.ung.kodeverk.Fagsystem as UngFagsystem
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

class PåkrevdFeltHistorikkvaskTest : AbstractK9LosIntegrationTest() {

    private val eksternId = UUID.randomUUID()
    private val opprettetBehandling = LocalDateTime.of(2026, 3, 1, 8, 0)

    private lateinit var eventRepository: EventRepository
    private lateinit var transactionalManager: TransactionalManager
    private lateinit var adapter: EventTilOppgaveAdapter
    private lateinit var oppgavetypeTjeneste: OppgavetypeTjeneste
    private lateinit var statistikkRepository: StatistikkRepository

    @BeforeEach
    fun setup() {
        get<AvpOmrådesetup>().setup()
        eventRepository = get()
        transactionalManager = get()
        adapter = get()
        oppgavetypeTjeneste = get()
        statistikkRepository = get()
    }

    @Test
    fun `felt som blir påkrevd uten defaultverdi bestiller historikkvask for oppgaver som mangler verdien`() {
        prosesserOppgave()

        oppgavetypeTjeneste.oppdater(oppgavetyperMedPåkrevdFelt(AktivitetspengerFelter.Saksbehandling.UTENLANDSSAK.id))

        assertThat(eventRepository.hentAntallHistorikkvaskbestillinger()).isEqualTo(1L)
    }

    @Test
    fun `felt som blir påkrevd bestiller ikke vask når alle versjoner har verdien`() {
        prosesserOppgave()

        oppgavetypeTjeneste.oppdater(oppgavetyperMedPåkrevdFelt(AktivitetspengerFelter.Sak.MOTTATT_DATO.id))

        assertThat(eventRepository.hentAntallHistorikkvaskbestillinger()).isEqualTo(0L)
    }

    @Test
    fun `dvh-sending holder tilbake oppgaver med åpen historikkvaskbestilling`() {
        prosesserOppgave()
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(Områder.AKTIVITETSPENGER)).isNotEmpty()

        eventRepository.bestillHistorikkvask(Fagsystem.UNGSAK, eksternId.toString())
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(Områder.AKTIVITETSPENGER)).isEmpty()

        transactionalManager.transaction { tx ->
            eventRepository.settHistorikkvaskFerdig(Fagsystem.UNGSAK, eksternId.toString(), tx)
        }
        assertThat(statistikkRepository.hentOppgaverSomIkkeErSendt(Områder.AKTIVITETSPENGER)).isNotEmpty()
    }

    private fun prosesserOppgave() {
        val event = lagEvent()
        transactionalManager.transaction { tx ->
            eventRepository.lagre(
                Fagsystem.UNGSAK,
                eksternId.toString(),
                event.eventTid.toString(),
                LosObjectMapper.instance.writeValueAsString(event),
                tx
            )
        }
        adapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.UNGSAK, eksternId.toString()))
    }

    private fun oppgavetyperMedPåkrevdFelt(feltId: String): OppgavetyperDto {
        val dto = AktivitetspengerOppgavetyper.tilDto("http://localhost")
        return dto.copy(
            oppgavetyper = dto.oppgavetyper.map { oppgavetype ->
                oppgavetype.copy(
                    oppgavefelter = oppgavetype.oppgavefelter.map { felt ->
                        if (felt.id == feltId) felt.copy(påkrevd = true) else felt
                    }.toSet()
                )
            }.toSet()
        )
    }

    private fun lagEvent(): UngSakEventDto {
        val aksjonspunkt = AksjonspunktDefinisjon.NAV_KONTOR_FORESLÅR_VILKÅR
        return UngSakEventDto(
            eksternId = eksternId,
            fagsystem = UngFagsystem.UNG_SAK,
            saksnummer = "AKT123456",
            aktørId = "1234567890123",
            eventTid = opprettetBehandling.plusHours(1),
            eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            behandlingSteg = aksjonspunkt.behandlingSteg.kode,
            behandlendeEnhet = AvpBehandlendeEnhet.STYRINGSENHET.kode,
            navKontorbehandlendeEnhet = AvpBehandlendeEnhet.FALKENBORG.kode,
            ansvarligBeslutterForTotrinn = null,
            ansvarligSaksbehandlerForTotrinn = null,
            navKontorAnsvarligSaksbehandler = "Z123456",
            navKontorBeslutter = null,
            resultatType = BehandlingResultatType.IKKE_FASTSATT.kode,
            ytelseTypeKode = FagsakYtelseType.AKTIVITETSPENGER.kode,
            behandlingTypeKode = BehandlingType.FØRSTEGANGSSØKNAD.kode,
            eldsteDatoMedEndringFraSøker = opprettetBehandling,
            opprettetBehandling = opprettetBehandling,
            fagsakPeriode = Periode(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 3, 1)),
            aksjonspunktTilstander = listOf(
                AksjonspunktTilstandDto(
                    aksjonspunkt.kode,
                    AksjonspunktStatus.OPPRETTET,
                    Venteårsak.UDEFINERT,
                    null,
                    null,
                    opprettetBehandling,
                    opprettetBehandling,
                )
            ),
            nyeKrav = false,
            vedtaksdato = null,
            behandlingstidFrist = LocalDate.of(2026, 4, 1),
            behandlingsårsaker = emptyList(),
        )
    }
}
