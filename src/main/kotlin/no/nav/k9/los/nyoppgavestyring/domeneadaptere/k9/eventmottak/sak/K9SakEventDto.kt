package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import no.nav.k9.kodeverk.produksjonsstyring.MerknadType
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.KodeverkDeserializer
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import no.nav.k9.sak.typer.Periode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class K9SakEventDto(

    /**
     * Ekstern id for behandlingen. Id benyttes til oppslag i fagsystem.
     * Benytt samme id for alle oppdateringer av aksjonspunkt/prosess innenfor samme behandling.
     */
    val eksternId: UUID?,
    val fagsystem: Fagsystem,
    val saksnummer: String,
    val aktørId: String,
    val vedtaksdato: LocalDate?,

    val behandlingId: Long?, // fjernes etter overgang til eksternId

    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val behandlingstidFrist: LocalDate?,
    /**
     * Tidspunkt for hendelse lokalt for fagsystem.
     */
    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    val eventTid: LocalDateTime,
    val eventHendelse: EventHendelse,
    val merknader: List<MerknadType> = emptyList(),

    @JsonAlias("behandlinStatus")
    val behandlingStatus: String?,
    val behandlingSteg: String?,
    val behandlendeEnhet: String? = null,
    val resultatType: String? = null,

    /**
     * Ytelsestype i kodeform. Eksempel: FP
     */
    val ytelseTypeKode: String,

    /**
     * Behandlingstype i kodeform. Eksempel: BT-002
     */
    val behandlingTypeKode: String,

    /**
     * Tidspunkt behandling ble opprettet
     */
    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    val opprettetBehandling: LocalDateTime,

    /**
     * Tidspunkt NAV har mottatt dokument som instigerte behandlingen
     */
    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    val eldsteDatoMedEndringFraSøker: LocalDateTime?,

    /**
     * Map av aksjonspunktkode og statuskode.
     */
    @Deprecated("bruk aksjonspunktTilstander")
    val aksjonspunktKoderMedStatusListe: MutableMap<String, String>? = null,

    val href: String? = null,
    val førsteFeilutbetaling: String? = null,
    val feilutbetaltBeløp: Long? = null,
    val ansvarligSaksbehandlerIdent: String? = null,
    val ansvarligSaksbehandlerForTotrinn: String? = null,
    val ansvarligBeslutterForTotrinn: String? = null,

    val fagsakPeriode: Periode? = null,

    val pleietrengendeAktørId: String? = null,
    val relatertPartAktørId: String? = null,
    val aksjonspunktTilstander: List<AksjonspunktTilstandDto> = emptyList(),
    val nyeKrav: Boolean? = null,
    val kunNyePerioder: Boolean? = null,
    val fraEndringsdialog: Boolean? = null,

    @JsonDeserialize(using = KodeverkDeserializer::class)
    val søknadsårsaker : List<String> = emptyList(),
    @JsonDeserialize(using = KodeverkDeserializer::class)
    val behandlingsårsaker: List<String> = emptyList(),
    val relevanteSøknadsperioder: List<Periode> = emptyList()
) {

    // Denne skal ikke ha fnr, aktørider, orgnumre eller beløp som kan identifisere brukeren
    fun tryggToString(): String {
        return """BehandlingProsessEventDto(
            eksternId=$eksternId, 
            fagsystem=$fagsystem, 
            saksnummer='$saksnummer', 
            behandlingstidFrist=$behandlingstidFrist, 
            eventTid=$eventTid, 
            eventHendelse=$eventHendelse, 
            behandlingStatus=$behandlingStatus, 
            behandlingSteg=$behandlingSteg, 
            behandlendeEnhet=$behandlendeEnhet, 
            resultatType=$resultatType, 
            ytelseTypeKode='$ytelseTypeKode', 
            behandlingTypeKode='$behandlingTypeKode', 
            opprettetBehandling=$opprettetBehandling, 
            aksjonspunktKoderMedStatusListe=$aksjonspunktKoderMedStatusListe, 
            ansvarligSaksbehandlerIdent=$ansvarligSaksbehandlerIdent, 
            ansvarligSaksbehandlerForTotrinn=$ansvarligSaksbehandlerForTotrinn, 
            ansvarligBeslutterForTotrinn=$ansvarligBeslutterForTotrinn, 
            fagsakPeriode=$fagsakPeriode,
            aksjonspunktTilstander=$aksjonspunktTilstander,
            nyeKrav=$nyeKrav
            fraEndringsdialog=$fraEndringsdialog,
            søknadsårsaker=$søknadsårsaker
            )"""
            .trimMargin()
    }
}