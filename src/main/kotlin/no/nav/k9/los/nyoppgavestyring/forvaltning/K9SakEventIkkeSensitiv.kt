package no.nav.k9.los.nyoppgavestyring.forvaltning

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.KodeverkDeserializer
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import no.nav.k9.sak.typer.Periode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class K9SakEventIkkeSensitiv(
    /**
     * Ekstern id for behandlingen. Id benyttes til oppslag i fagsystem.
     * Benytt samme id for alle oppdateringer av aksjonspunkt/prosess innenfor samme behandling.
     */
    val eksternId: UUID?,
    val fagsystem: Fagsystem,
    val saksnummer: String,
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

    val href: String? = null,
    val førsteFeilutbetaling: String? = null,
    val feilutbetaltBeløp: Long? = null,
    val ansvarligSaksbehandlerIdent: String? = null,
    val ansvarligSaksbehandlerForTotrinn: String? = null,
    val ansvarligBeslutterForTotrinn: String? = null,

    val fagsakPeriode: Periode? = null,

    val aksjonspunktTilstander: List<AksjonspunktTilstandDto> = emptyList(),
    val nyeKrav: Boolean? = null,
    val fraEndringsdialog: Boolean? = null,

    @JsonDeserialize(using = KodeverkDeserializer::class)
    val søknadsårsaker : List<String> = emptyList(),
    @JsonDeserialize(using = KodeverkDeserializer::class)
    val behandlingsårsaker: List<String> = emptyList()
) {
    constructor(event: K9SakEventDto) : this(
        eksternId = event.eksternId,
        fagsystem = event.fagsystem,
        saksnummer = event.saksnummer,
        vedtaksdato = event.vedtaksdato,
        behandlingId = event.behandlingId,
        behandlingstidFrist = event.behandlingstidFrist,
        eventTid = event.eventTid,
        eventHendelse = event.eventHendelse,
        behandlingStatus = event.behandlingStatus,
        behandlingSteg = event.behandlingSteg,
        behandlendeEnhet = event.behandlendeEnhet,
        resultatType = event.resultatType,
        ytelseTypeKode = event.ytelseTypeKode,
        behandlingTypeKode = event.behandlingTypeKode,
        opprettetBehandling = event.opprettetBehandling,
        eldsteDatoMedEndringFraSøker = event.eldsteDatoMedEndringFraSøker,
        href = event.href,
        førsteFeilutbetaling = event.førsteFeilutbetaling,
        feilutbetaltBeløp = event.feilutbetaltBeløp,
        ansvarligSaksbehandlerIdent = event.ansvarligSaksbehandlerIdent,
        ansvarligSaksbehandlerForTotrinn = event.ansvarligSaksbehandlerForTotrinn,
        ansvarligBeslutterForTotrinn = event.ansvarligBeslutterForTotrinn,
        fagsakPeriode = event.fagsakPeriode,
        aksjonspunktTilstander = event.aksjonspunktTilstander,
        nyeKrav = event.nyeKrav,
        fraEndringsdialog = event.fraEndringsdialog,
        søknadsårsaker = event.søknadsårsaker,
        behandlingsårsaker = event.behandlingsårsaker,
    )
}

