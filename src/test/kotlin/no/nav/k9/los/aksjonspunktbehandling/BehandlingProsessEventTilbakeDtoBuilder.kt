package no.nav.k9.los.aksjonspunktbehandling

import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktDefinisjonK9Tilbake.Companion.AVKLART_FAKTA_FEILUTBETALING
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktDefinisjonK9Tilbake.Companion.FATTE_VEDTAK
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktDefinisjonK9Tilbake.Companion.FORESLÅ_VEDTAK
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktDefinisjonK9Tilbake.Companion.VENT_PÅ_BRUKERTILBAKEMELDING
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktDefinisjonK9Tilbake.Companion.VENT_PÅ_TILBAKEKREVINGSGRUNNLAG
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktDefinisjonK9Tilbake.Companion.VURDER_FORELDELSE
import no.nav.k9.los.aksjonspunktbehandling.AksjonspunktDefinisjonK9Tilbake.Companion.VURDER_TILBAKEKREVING
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventTilbakeDto
import no.nav.k9.los.integrasjon.kafka.dto.EventHendelse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class BehandlingProsessEventTilbakeDtoBuilder(
    var eksternId: UUID = UUID.randomUUID(),
    var fagsystem: Fagsystem = Fagsystem.K9TILBAKE,
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
    fun opprettet(): BehandlingProsessEventTilbakeDtoBuilder {
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.behandlingSteg = "FAKTFEILUTSTEG"
        this.aksjonspunkter =  mutableMapOf(
            VENT_PÅ_BRUKERTILBAKEMELDING to AksjonspunktStatus.UTFØRT,
            AVKLART_FAKTA_FEILUTBETALING to AksjonspunktStatus.OPPRETTET,
        )
        this.ansvarligSaksbehandlerIdent = null
        return this
    }

    fun foreslåVedtak(ansvarligSaksbehandler: Saksbehandler? = TestSaksbehandler.SARA): BehandlingProsessEventTilbakeDtoBuilder {
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.behandlingSteg = BehandlingStegType.FORESLÅ_VEDTAK.kode
        this.aksjonspunkter = mutableMapOf(
            VURDER_TILBAKEKREVING to AksjonspunktStatus.UTFØRT,
            VURDER_FORELDELSE to AksjonspunktStatus.UTFØRT,
            FORESLÅ_VEDTAK to AksjonspunktStatus.OPPRETTET,
            VENT_PÅ_BRUKERTILBAKEMELDING to AksjonspunktStatus.UTFØRT,
            AVKLART_FAKTA_FEILUTBETALING to AksjonspunktStatus.UTFØRT,
        )
        this.ansvarligSaksbehandlerIdent = ansvarligSaksbehandler?.brukerIdent
        return this
    }

    fun hosBeslutter(ansvarligSaksbehandler: Saksbehandler? = TestSaksbehandler.SARA): BehandlingProsessEventTilbakeDtoBuilder {
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.behandlingSteg = BehandlingStegType.FATTE_VEDTAK.kode
        this.aksjonspunkter = mutableMapOf(
            VURDER_TILBAKEKREVING to AksjonspunktStatus.UTFØRT,
            VURDER_FORELDELSE to AksjonspunktStatus.UTFØRT,
            FORESLÅ_VEDTAK to AksjonspunktStatus.UTFØRT,
            FATTE_VEDTAK to AksjonspunktStatus.OPPRETTET,
            VENT_PÅ_BRUKERTILBAKEMELDING to AksjonspunktStatus.UTFØRT,
            AVKLART_FAKTA_FEILUTBETALING to AksjonspunktStatus.UTFØRT,
        )
        this.ansvarligSaksbehandlerIdent = ansvarligSaksbehandler?.brukerIdent
        return this
    }

    fun returFraBeslutter(): BehandlingProsessEventTilbakeDtoBuilder{
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.behandlingSteg = BehandlingStegType.FORESLÅ_VEDTAK.kode
        this.aksjonspunkter = mutableMapOf(
            VURDER_TILBAKEKREVING to AksjonspunktStatus.UTFØRT,
            VURDER_FORELDELSE to AksjonspunktStatus.UTFØRT,
            FORESLÅ_VEDTAK to AksjonspunktStatus.OPPRETTET,
            FATTE_VEDTAK to AksjonspunktStatus.AVBRUTT,
            VENT_PÅ_BRUKERTILBAKEMELDING to AksjonspunktStatus.UTFØRT,
            AVKLART_FAKTA_FEILUTBETALING to AksjonspunktStatus.UTFØRT,
        )
        return this
    }

    fun avsluttet(ansvarligBeslutter: Saksbehandler? = TestSaksbehandler.BIRGER_BESLUTTER): BehandlingProsessEventTilbakeDtoBuilder {
        this.behandlingStatus = BehandlingStatus.AVSLUTTET
        this.behandlingSteg = null
        this.aksjonspunkter = mutableMapOf(
            VURDER_TILBAKEKREVING to AksjonspunktStatus.UTFØRT,
            VURDER_FORELDELSE to AksjonspunktStatus.UTFØRT,
            FORESLÅ_VEDTAK to AksjonspunktStatus.UTFØRT,
            FATTE_VEDTAK to AksjonspunktStatus.UTFØRT,
            VENT_PÅ_BRUKERTILBAKEMELDING to AksjonspunktStatus.UTFØRT,
            VENT_PÅ_TILBAKEKREVINGSGRUNNLAG to AksjonspunktStatus.UTFØRT,
            AVKLART_FAKTA_FEILUTBETALING to AksjonspunktStatus.UTFØRT,
        )
        this.ansvarligSaksbehandlerForTotrinn = ansvarligBeslutter?.brukerIdent
        return this
    }

    fun build(): BehandlingProsessEventTilbakeDto {
        return BehandlingProsessEventTilbakeDto(
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
            fagsystem = fagsystem.kode,
            behandlinStatus = behandlingStatus.kode
        )
    }
}