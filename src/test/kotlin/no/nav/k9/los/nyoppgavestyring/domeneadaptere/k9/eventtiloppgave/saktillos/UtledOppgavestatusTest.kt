package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos
import no.nav.k9.kodeverk.behandling.*
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*


class UtledOppgavestatusTest {

    @Test
    fun `opprettet behandling gir automatisk oppgave siden prosessen i k9sak ikke har startet ennå`() {
        assertEquals(
            Oppgavestatus.AUTOMATISK,
            EventTilDtoMapper.utledOppgavestatus(testevent(BehandlingStatus.OPPRETTET, emptyList()))
        )
    }

    @Test
    fun `avsluttet behandling gir lukket oppgave`() {
        assertEquals(
            Oppgavestatus.LUKKET,
            EventTilDtoMapper.utledOppgavestatus(testevent(BehandlingStatus.AVSLUTTET, emptyList()))
        )
    }

    @Test
    fun `IVERKSETTER_VEDTAK gir lukket oppgave siden nye aksjonspunkter ikke kan oppstå`() {
        assertEquals(
            Oppgavestatus.LUKKET,
            EventTilDtoMapper.utledOppgavestatus(testevent(BehandlingStatus.IVERKSETTER_VEDTAK, emptyList()))
        )
    }

    @Test
    fun `behandling utredes med manuelt aksjonspunkt gir åpen oppgave`() {
        assertEquals(
            Oppgavestatus.AAPEN,
            EventTilDtoMapper.utledOppgavestatus(
                testevent(
                    BehandlingStatus.UTREDES,
                    listOf(testAksjonspunktTilstand("9001", AksjonspunktStatus.OPPRETTET))))
        )
    }

    @Test
    fun `åpen behandling med autopunkt gir oppgave på vent`() {
        assertEquals(
            Oppgavestatus.AAPEN,
            EventTilDtoMapper.utledOppgavestatus(
                testevent(
                    BehandlingStatus.UTREDES,
                    listOf(testAksjonspunktTilstand("7003", AksjonspunktStatus.OPPRETTET))))
        )

        assertEquals(
            Oppgavestatus.AAPEN,
            EventTilDtoMapper.utledOppgavestatus(
                testevent(
                    BehandlingStatus.IVERKSETTER_VEDTAK,
                    listOf(testAksjonspunktTilstand("7003", AksjonspunktStatus.OPPRETTET))))
        )

        assertEquals(
            Oppgavestatus.AAPEN,
            EventTilDtoMapper.utledOppgavestatus(
                testevent(
                    BehandlingStatus.FATTER_VEDTAK,
                    listOf(testAksjonspunktTilstand("7003", AksjonspunktStatus.OPPRETTET))))
        )
    }

    fun testAksjonspunktTilstand(apKode: String, status: AksjonspunktStatus): AksjonspunktTilstandDto {
        return AksjonspunktTilstandDto(
            apKode,
            status,
            Venteårsak.AVV_DOK,
            "Sara Saksbehandler",
            LocalDateTime.now().plusDays(30),
            LocalDateTime.now(),
            LocalDateTime.now(),
        )
    }

    fun testevent(status: BehandlingStatus, aksjonspunkter: List<AksjonspunktTilstandDto>): K9SakEventDto {
        return K9SakEventDto(
            eksternId = UUID.randomUUID(),
            fagsystem = Fagsystem.K9SAK,
            saksnummer = Random().nextInt(0, 200).toString(),
            aktørId = Random().nextInt(0, 9999999).toString(),
            behandlingId = 123L,
            resultatType = BehandlingResultatType.IKKE_FASTSATT.kode,
            behandlendeEnhet = null,
            aksjonspunktTilstander = aksjonspunkter,
            søknadsårsaker = emptyList(),
            behandlingsårsaker = emptyList(),
            ansvarligSaksbehandlerIdent = null,
            ansvarligBeslutterForTotrinn = null,
            ansvarligSaksbehandlerForTotrinn = null,
            opprettetBehandling = LocalDateTime.now(),
            vedtaksdato = LocalDate.now(),
            pleietrengendeAktørId = Random().nextInt(0, 9999999).toString(),
            behandlingStatus = status.kode,
            behandlingSteg = BehandlingStegType.KONTROLLER_FAKTA.kode,
            behandlingTypeKode = BehandlingType.FØRSTEGANGSSØKNAD.kode,
            behandlingstidFrist = null,
            eventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
            eventTid = LocalDateTime.now(),
            aksjonspunktKoderMedStatusListe = mutableMapOf(),
            ytelseTypeKode = FagsakYtelseType.PLEIEPENGER_SYKT_BARN.kode,
            eldsteDatoMedEndringFraSøker = LocalDateTime.now(),
        )
    }
}