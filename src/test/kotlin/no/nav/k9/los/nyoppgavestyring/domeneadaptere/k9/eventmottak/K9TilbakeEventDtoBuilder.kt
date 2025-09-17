package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak

import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.AksjonspunktDefinisjonK9Tilbake
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class K9TilbakeEventDtoBuilder(
    var eksternId: UUID = UUID.randomUUID(),
    var fagsystem: Fagsystem = Fagsystem.TILBAKE,
    var saksnummer: String = Random().nextInt(0, 200).toString(),
    var aktørId: String = Random().nextInt(0, 9999999).toString(),
    var behandlingstidFrist: LocalDate? = null,
    var eventTid: LocalDateTime? = null,
    var eventHendelse: EventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
    var behandlingStatus: BehandlingStatus = BehandlingStatus.UTREDES,
    var behandlingSteg: String? = BehandlingStegType.FATTE_VEDTAK.kode,
    var behandlendeEnhet: String? = null,
    var ytelseTypeKode: FagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
    var opprettetBehandling: LocalDateTime = LocalDateTime.now(),
    var aksjonspunkter: MutableMap<AksjonspunktDefinisjonK9Tilbake, AksjonspunktStatus> = mutableMapOf(),
    var ansvarligSaksbehandlerIdent: String? = null,
    var ansvarligSaksbehandlerForTotrinn: String? = null,
    private var teller: Long = 0
) {
    fun opprettet(): K9TilbakeEventDtoBuilder {
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.behandlingSteg = "FAKTFEILUTSTEG"
        this.aksjonspunkter =  mutableMapOf(
            AksjonspunktDefinisjonK9Tilbake.VENT_PÅ_BRUKERTILBAKEMELDING to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.AVKLART_FAKTA_FEILUTBETALING to AksjonspunktStatus.OPPRETTET,
        )
        this.ansvarligSaksbehandlerIdent = null
        return this
    }

    fun foreslåVedtak(ansvarligSaksbehandler: Saksbehandler? = TestSaksbehandler.SARA): K9TilbakeEventDtoBuilder {
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.behandlingSteg = BehandlingStegType.FORESLÅ_VEDTAK.kode
        this.aksjonspunkter = mutableMapOf(
            AksjonspunktDefinisjonK9Tilbake.VURDER_TILBAKEKREVING to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.VURDER_FORELDELSE to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.FORESLÅ_VEDTAK to AksjonspunktStatus.OPPRETTET,
            AksjonspunktDefinisjonK9Tilbake.VENT_PÅ_BRUKERTILBAKEMELDING to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.AVKLART_FAKTA_FEILUTBETALING to AksjonspunktStatus.UTFØRT,
        )
        this.ansvarligSaksbehandlerIdent = ansvarligSaksbehandler?.brukerIdent
        return this
    }

    fun hosBeslutter(ansvarligSaksbehandler: Saksbehandler? = TestSaksbehandler.SARA): K9TilbakeEventDtoBuilder {
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.behandlingSteg = BehandlingStegType.FATTE_VEDTAK.kode
        this.aksjonspunkter = mutableMapOf(
            AksjonspunktDefinisjonK9Tilbake.VURDER_TILBAKEKREVING to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.VURDER_FORELDELSE to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.FORESLÅ_VEDTAK to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.FATTE_VEDTAK to AksjonspunktStatus.OPPRETTET,
            AksjonspunktDefinisjonK9Tilbake.VENT_PÅ_BRUKERTILBAKEMELDING to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.AVKLART_FAKTA_FEILUTBETALING to AksjonspunktStatus.UTFØRT,
        )
        this.ansvarligSaksbehandlerIdent = ansvarligSaksbehandler?.brukerIdent
        return this
    }

    fun returFraBeslutter(): K9TilbakeEventDtoBuilder {
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.behandlingSteg = BehandlingStegType.FORESLÅ_VEDTAK.kode
        this.aksjonspunkter = mutableMapOf(
            AksjonspunktDefinisjonK9Tilbake.VURDER_TILBAKEKREVING to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.VURDER_FORELDELSE to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.FORESLÅ_VEDTAK to AksjonspunktStatus.OPPRETTET,
            AksjonspunktDefinisjonK9Tilbake.FATTE_VEDTAK to AksjonspunktStatus.AVBRUTT,
            AksjonspunktDefinisjonK9Tilbake.VENT_PÅ_BRUKERTILBAKEMELDING to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.AVKLART_FAKTA_FEILUTBETALING to AksjonspunktStatus.UTFØRT,
        )
        return this
    }

    fun avsluttet(ansvarligBeslutter: Saksbehandler? = TestSaksbehandler.BIRGER_BESLUTTER): K9TilbakeEventDtoBuilder {
        this.behandlingStatus = BehandlingStatus.AVSLUTTET
        this.behandlingSteg = null
        this.aksjonspunkter = mutableMapOf(
            AksjonspunktDefinisjonK9Tilbake.VURDER_TILBAKEKREVING to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.VURDER_FORELDELSE to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.FORESLÅ_VEDTAK to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.FATTE_VEDTAK to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.VENT_PÅ_BRUKERTILBAKEMELDING to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.VENT_PÅ_TILBAKEKREVINGSGRUNNLAG to AksjonspunktStatus.UTFØRT,
            AksjonspunktDefinisjonK9Tilbake.AVKLART_FAKTA_FEILUTBETALING to AksjonspunktStatus.UTFØRT,
        )
        this.ansvarligSaksbehandlerForTotrinn = ansvarligBeslutter?.brukerIdent
        return this
    }

    fun build(): K9TilbakeEventDto {
        return K9TilbakeEventDto(
            eksternId = eksternId,
            saksnummer = saksnummer,
            behandlingId = 123L,
            resultatType = null,
            behandlendeEnhet = behandlendeEnhet,
            ansvarligSaksbehandlerIdent = ansvarligSaksbehandlerIdent,
            opprettetBehandling = opprettetBehandling,
            aktørId = aktørId,
            behandlingStatus = behandlingStatus.kode,
            behandlingSteg = behandlingSteg,
            behandlingTypeKode = "BT-007",
            behandlingstidFrist = behandlingstidFrist,
            eventHendelse = eventHendelse,
            eventTid = eventTid ?: LocalDateTime.now().plusSeconds(teller++),
            aksjonspunktKoderMedStatusListe = aksjonspunkter.entries.associate { (aksjonspunkt, status) -> aksjonspunkt.kode to status.kode }.toMutableMap(),
            ytelseTypeKode = ytelseTypeKode.kode,
            ansvarligBeslutterIdent = ansvarligSaksbehandlerForTotrinn,
            førsteFeilutbetaling = null,
            feilutbetaltBeløp = 100L,
            href = null,
            fagsystem = fagsystem.kode
        )
    }
}