package no.nav.k9.los.aksjonspunktbehandling

import no.nav.k9.kodeverk.behandling.BehandlingResultatType
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.BehandlingType
import no.nav.k9.kodeverk.behandling.BehandlingÅrsakType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.kodeverk.uttak.SøknadÅrsak
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.FagsakYtelseType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.EventHendelse
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
    var ytelseType: FagsakYtelseType = FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
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

    fun manueltSattPåVentMedisinskeOpplysninger(): BehandlingProsessEventDtoBuilder {
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET
        this.behandlingSteg = BehandlingStegType.VURDER_MEDISINSKE_VILKÅR
        this.resultatType = BehandlingResultatType.IKKE_FASTSATT
        this.aksjonspunkter = mutableListOf(
            AksjonspunktTilstandBuilder.KONTROLLER_LEGEERKLÆRING.medStatus(AksjonspunktStatus.UTFØRT),
            AksjonspunktDefinisjon.AUTO_MANUELT_SATT_PÅ_VENT.builder().medStatus(AksjonspunktStatus.OPPRETTET)
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
            AksjonspunktTilstandBuilder.FORESLÅ_VEDTAK.medStatus(AksjonspunktStatus.AVBRUTT),
            AksjonspunktTilstandBuilder.FATTER_VEDTAK.medStatus(AksjonspunktStatus.UTFØRT)
        )
        return this
    }

    // Basert på eventer ved retur vurder opptjening på nytt
    fun returFraBeslutterOpptjening(): BehandlingProsessEventDtoBuilder{
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.behandlingSteg = BehandlingStegType.VURDER_OPPTJENINGSVILKÅR
        this.resultatType = BehandlingResultatType.IKKE_FASTSATT
        this.aksjonspunkter = mutableListOf(
            AksjonspunktDefinisjon.VURDER_OPPTJENINGSVILKÅRET.builder().medStatus(AksjonspunktStatus.OPPRETTET),
            AksjonspunktDefinisjon.FASTSETT_BEREGNINGSGRUNNLAG_ARBEIDSTAKER_FRILANS.builder().medStatus(AksjonspunktStatus.AVBRUTT),
            AksjonspunktTilstandBuilder.KONTROLLER_LEGEERKLÆRING.medStatus(AksjonspunktStatus.UTFØRT),
            AksjonspunktTilstandBuilder.FORESLÅ_VEDTAK.medStatus(AksjonspunktStatus.AVBRUTT),
            AksjonspunktTilstandBuilder.FATTER_VEDTAK.medStatus(AksjonspunktStatus.UTFØRT),
            AksjonspunktDefinisjon.AUTO_MANUELT_SATT_PÅ_VENT.builder().medStatus(AksjonspunktStatus.UTFØRT).medVenteårsakOgFrist(Venteårsak.AVV_DOK, LocalDateTime.now().minusDays(1))
        )
        return this
    }

    // Transient tilstand under iverksetting av vedtak. Brukes f.eks i tester som sjekker toleranse for rekkefølgefeil i eventer fra k9-sak.
    fun beslutterGodkjent(ansvarligBeslutter: Saksbehandler? = TestSaksbehandler.BIRGER_BESLUTTER): BehandlingProsessEventDtoBuilder {
        this.behandlingStatus = BehandlingStatus.UTREDES
        this.behandlingSteg = BehandlingStegType.FATTE_VEDTAK
        this.resultatType = BehandlingResultatType.INNVILGET
        this.aksjonspunkter = mutableListOf(
            AksjonspunktTilstandBuilder.KONTROLLER_LEGEERKLÆRING.medStatus(AksjonspunktStatus.UTFØRT),
            AksjonspunktTilstandBuilder.FORESLÅ_VEDTAK.medStatus(AksjonspunktStatus.UTFØRT),
            AksjonspunktTilstandBuilder.FATTER_VEDTAK.medStatus(AksjonspunktStatus.UTFØRT)
        )
        this.ansvarligSaksbehandlerForTotrinn = ansvarligBeslutter?.brukerIdent
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

    fun medAksjonspunkt(vararg aksjonspunkter: AksjonspunktTilstandBuilder): BehandlingProsessEventDtoBuilder {
        this.aksjonspunkter = aksjonspunkter.toMutableList()
        return this
    }

    fun medBehandlingSteg(behandlingSteg: BehandlingStegType): BehandlingProsessEventDtoBuilder {
        this.behandlingSteg = behandlingSteg
        return this
    }

    fun medBehandlingStatus(behandlingStatus: BehandlingStatus): BehandlingProsessEventDtoBuilder {
        this.behandlingStatus = behandlingStatus
        return this
    }

    fun build(overstyrRekkefølge: Long? = null): BehandlingProsessEventDto {
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
            eventTid = eventTid ?: overstyrRekkefølge ?.let { LocalDateTime.now().plusSeconds(overstyrRekkefølge) } ?: LocalDateTime.now().plusSeconds(teller++),
            aksjonspunktKoderMedStatusListe = aksjonspunkter.associate { it.kode to it.status.kode }.toMutableMap(),
            ytelseTypeKode = ytelseType.kode,
            eldsteDatoMedEndringFraSøker = LocalDateTime.now()
        )
    }
}
