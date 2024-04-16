package no.nav.k9.los.aksjonspunktbehandling

import no.nav.k9.kodeverk.behandling.*
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.kodeverk.uttak.SøknadÅrsak
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class BehandlingProsessEventDtoBuilder(
    var eksternId: UUID = UUID.randomUUID(),
    var fagsystem: Fagsystem = Fagsystem.K9SAK,
    var saksnummer: String = Random().nextInt(0, 200).toString(),
    var aktørId: String = Random().nextInt(0, 9999999).toString(),
    var pleietrengendeAktørId: String = Random().nextInt(0, 9999999).toString(),
    var vedtaksdato: LocalDate = LocalDate.now(),
    var behandlingstidFrist: LocalDate? = null,
    var eventTid: LocalDateTime? = null,
    var eventHendelse: EventHendelse = EventHendelse.BEHANDLINGSKONTROLL_EVENT,
    var behandlingStatus: BehandlingStatus = BehandlingStatus.UTREDES,
    var behandlingSteg: BehandlingStegType = BehandlingStegType.KONTROLLER_FAKTA,
    var behandlendeEnhet: String? = null,
    var resultatType: BehandlingResultatType = BehandlingResultatType.IKKE_FASTSATT,
    var ytelseTypeKode: FagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
    var behandlingTypeKode: BehandlingType = BehandlingType.FØRSTEGANGSSØKNAD,
    var opprettetBehandling: LocalDateTime = LocalDateTime.now(),
    var aksjonspunkter: MutableList<AksjonspunktTilstandBuilder> = mutableListOf(),
    var fraEndringsdialog: Boolean = false,
    var søknadÅrsaker: List<SøknadÅrsak> = mutableListOf(),
    var behandlingÅrsaker: List<BehandlingÅrsakType> = mutableListOf(),
    var ansvarligSaksbehandlerIdent: String? = null,
    var ansvarligSaksbehandlerForTotrinn: String? = null,
    private var teller: Long = 0
) {
    fun opprettet(): BehandlingProsessEventDtoBuilder {
        this.resultatType = BehandlingResultatType.IKKE_FASTSATT
        this.behandlingStatus = BehandlingStatus.OPPRETTET
        this.resultatType = BehandlingResultatType.IKKE_FASTSATT
        this.aksjonspunkter = mutableListOf()
        this.ansvarligSaksbehandlerIdent = null
        return this
    }

    fun vurderSykdom(): BehandlingProsessEventDtoBuilder {
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.behandlingSteg = BehandlingStegType.VURDER_MEDISINSKE_VILKÅR
        this.resultatType = BehandlingResultatType.IKKE_FASTSATT
        this.aksjonspunkter = mutableListOf(
            AksjonspunktTilstandBuilder.KONTROLLER_LEGEERKLÆRING.medStatus(AksjonspunktStatus.OPPRETTET)
        )
        return this
    }

    fun venterPåInntektsmelding(): BehandlingProsessEventDtoBuilder {
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.resultatType = BehandlingResultatType.IKKE_FASTSATT
        this.aksjonspunkter = mutableListOf(
            AksjonspunktTilstandBuilder.KONTROLLER_LEGEERKLÆRING.medStatus(AksjonspunktStatus.UTFØRT),
            AksjonspunktTilstandBuilder.VENTER_PÅ_IM.medStatus(AksjonspunktStatus.OPPRETTET)
        )
        return this
    }

    fun foreslåVedtak(): BehandlingProsessEventDtoBuilder {
       this.behandlingStatus = BehandlingStatus.UTREDES
       this.behandlingSteg = BehandlingStegType.FORESLÅ_VEDTAK
       this.resultatType = BehandlingResultatType.INNVILGET
       this.aksjonspunkter = mutableListOf(
            AksjonspunktTilstandBuilder.KONTROLLER_LEGEERKLÆRING.medStatus(AksjonspunktStatus.UTFØRT),
            AksjonspunktTilstandBuilder.FORESLÅ_VEDTAK.medStatus(AksjonspunktStatus.OPPRETTET)
        )
        return this
    }

    fun hosBeslutter(ansvarligSaksbehandler: Saksbehandler? = TestSaksbehandler.SARA): BehandlingProsessEventDtoBuilder {
        this.behandlingStatus = BehandlingStatus.FATTER_VEDTAK
        this.behandlingSteg = BehandlingStegType.FATTE_VEDTAK
        this.resultatType = BehandlingResultatType.INNVILGET
        this.aksjonspunkter = mutableListOf(
            AksjonspunktTilstandBuilder.KONTROLLER_LEGEERKLÆRING.medStatus(AksjonspunktStatus.UTFØRT),
            AksjonspunktTilstandBuilder.FORESLÅ_VEDTAK.medStatus(AksjonspunktStatus.UTFØRT),
            AksjonspunktTilstandBuilder.FATTER_VEDTAK.medStatus(AksjonspunktStatus.OPPRETTET)
        )
        this.ansvarligSaksbehandlerIdent = ansvarligSaksbehandler?.brukerIdent
        return this
    }

    fun returFraBeslutter(): BehandlingProsessEventDtoBuilder{
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.behandlingSteg = BehandlingStegType.FORESLÅ_VEDTAK
        this.resultatType = BehandlingResultatType.INNVILGET
        this.aksjonspunkter = mutableListOf(
            AksjonspunktTilstandBuilder.KONTROLLER_LEGEERKLÆRING.medStatus(AksjonspunktStatus.UTFØRT),
            AksjonspunktTilstandBuilder.FORESLÅ_VEDTAK.medStatus(AksjonspunktStatus.OPPRETTET),
            AksjonspunktTilstandBuilder.FATTER_VEDTAK.medStatus(AksjonspunktStatus.AVBRUTT)
        )
        return this
    }

    fun avsluttet(ansvarligBeslutter: Saksbehandler? = TestSaksbehandler.BIRGER_BESLUTTER): BehandlingProsessEventDtoBuilder {
        this.behandlingStatus = BehandlingStatus.AVSLUTTET
        this.behandlingSteg = BehandlingStegType.IVERKSETT_VEDTAK
        this.resultatType = BehandlingResultatType.INNVILGET
        this.aksjonspunkter = mutableListOf(
            AksjonspunktTilstandBuilder.KONTROLLER_LEGEERKLÆRING.medStatus(AksjonspunktStatus.UTFØRT),
            AksjonspunktTilstandBuilder.FORESLÅ_VEDTAK.medStatus(AksjonspunktStatus.UTFØRT),
            AksjonspunktTilstandBuilder.FATTER_VEDTAK.medStatus(AksjonspunktStatus.UTFØRT)
        )
        this.ansvarligSaksbehandlerForTotrinn = ansvarligBeslutter?.brukerIdent
        return this
    }

    fun medPleietrengendeAktør(pleietrengendeAktørId: String): BehandlingProsessEventDtoBuilder {
        this.pleietrengendeAktørId = pleietrengendeAktørId
        return this
    }

    fun medEksternId(eksternId: UUID): BehandlingProsessEventDtoBuilder {
        this.eksternId = eksternId
        return this
    }

    fun build(): BehandlingProsessEventDto {
        return BehandlingProsessEventDto(
            eksternId,
            fagsystem,
            saksnummer,
            behandlingId = 123L,
            fraEndringsdialog = fraEndringsdialog,
            resultatType = resultatType.kode,
            behandlendeEnhet = behandlendeEnhet,
            aksjonspunktTilstander = aksjonspunkter.map {it.build() },
            søknadsårsaker = søknadÅrsaker.map { it.kode },
            behandlingsårsaker = behandlingÅrsaker.map { it.kode },
            ansvarligSaksbehandlerIdent = ansvarligSaksbehandlerIdent,
            ansvarligBeslutterForTotrinn = ansvarligSaksbehandlerForTotrinn,
            ansvarligSaksbehandlerForTotrinn = ansvarligSaksbehandlerForTotrinn,
            opprettetBehandling = opprettetBehandling,
            vedtaksdato = vedtaksdato,
            pleietrengendeAktørId = pleietrengendeAktørId,
            aktørId = aktørId,
            behandlingStatus = behandlingStatus.kode,
            behandlingSteg = behandlingSteg.kode,
            behandlingTypeKode = behandlingTypeKode.kode,
            behandlingstidFrist = behandlingstidFrist,
            eventHendelse = eventHendelse,
            eventTid = eventTid ?: LocalDateTime.now().plusSeconds(teller++),
            aksjonspunktKoderMedStatusListe = aksjonspunkter.associate { it.kode to it.status.kode }.toMutableMap(),
            ytelseTypeKode = ytelseTypeKode.kode,
            eldsteDatoMedEndringFraSøker = LocalDateTime.now()
        )
    }
}

data class AksjonspunktTilstandBuilder(
    val kode: String,
    var status: AksjonspunktStatus = AksjonspunktStatus.OPPRETTET,
    var venteårsak: Venteårsak? = Venteårsak.UDEFINERT,
    var frist: LocalDateTime? = null,
    val opprettet: LocalDateTime = LocalDateTime.now(),
    val endret: LocalDateTime = LocalDateTime.now(),
) {
    fun build(): AksjonspunktTilstandDto {
        return AksjonspunktTilstandDto(
            kode,
            status,
            venteårsak,
            "saksbehandler",
            frist,
            opprettet,
            endret
        )
    }

    fun medStatus(status: AksjonspunktStatus): AksjonspunktTilstandBuilder {
        this.status = status
        return this
    }

    fun medVenteårsakOgFrist(venteårsak: Venteårsak, frist: LocalDateTime? = LocalDateTime.now().plusWeeks(1)): AksjonspunktTilstandBuilder {
        this.venteårsak = venteårsak
        this.frist = frist
        return this
    }

    companion object {
        val KONTROLLER_LEGEERKLÆRING = AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING.builder().medStatus(AksjonspunktStatus.OPPRETTET)
        val VENTER_PÅ_IM = AksjonspunktDefinisjon.AUTO_VENT_ETTERLYST_INNTEKTSMELDING.builder()
            .medStatus(AksjonspunktStatus.OPPRETTET)
            .medVenteårsakOgFrist(Venteårsak.VENTER_PÅ_ETTERLYST_INNTEKTSMELDINGER)
        val FORESLÅ_VEDTAK = AksjonspunktDefinisjon.FORESLÅ_VEDTAK.builder().medStatus(AksjonspunktStatus.OPPRETTET)
        val FATTER_VEDTAK = AksjonspunktDefinisjon.FATTER_VEDTAK.builder().medStatus(AksjonspunktStatus.OPPRETTET)
    }
}

fun AksjonspunktDefinisjon.builder(): AksjonspunktTilstandBuilder {
    return AksjonspunktTilstandBuilder(kode)
}
