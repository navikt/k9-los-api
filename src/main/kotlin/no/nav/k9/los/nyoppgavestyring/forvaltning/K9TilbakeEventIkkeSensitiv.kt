package no.nav.k9.los.nyoppgavestyring.forvaltning

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class K9TilbakeEventIkkeSensitiv(
    /**
     * Ekstern id for behandlingen. Id benyttes til oppslag i fagsystem.
     * Benytt samme id for alle oppdateringer av aksjonspunkt/prosess innenfor samme behandling.
     */
    val eksternId: UUID?,
    val fagsystem: String,
    val saksnummer: String,

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
    val behandlinStatus: String?,
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
     * Map av aksjonspunktkode og statuskode.
     */
    val aksjonspunktKoderMedStatusListe: MutableMap<String, String>,

    val href: String? = null,
    val førsteFeilutbetaling: String? = null,
    val feilutbetaltBeløp: Long? = null,
    val ansvarligSaksbehandlerIdent: String? = null,
    val ansvarligBeslutterIdent: String? = null
) {
    constructor(event: K9TilbakeEventDto) : this(
        eksternId = event.eksternId,
        fagsystem = event.fagsystem,
        saksnummer = event.saksnummer,
        behandlingId = event.behandlingId,
        behandlingstidFrist = event.behandlingstidFrist,
        eventTid = event.eventTid,
        eventHendelse = event.eventHendelse,
        behandlingStatus = event.behandlingStatus,
        behandlinStatus = event.behandlinStatus,
        behandlingSteg = event.behandlingSteg,
        behandlendeEnhet = event.behandlendeEnhet,
        resultatType = event.resultatType,
        ytelseTypeKode = event.ytelseTypeKode,
        behandlingTypeKode = event.behandlingTypeKode,
        opprettetBehandling = event.opprettetBehandling,
        aksjonspunktKoderMedStatusListe = event.aksjonspunktKoderMedStatusListe,
        førsteFeilutbetaling = event.førsteFeilutbetaling,
        feilutbetaltBeløp = event.feilutbetaltBeløp,
        ansvarligSaksbehandlerIdent = event.ansvarligSaksbehandlerIdent,
        ansvarligBeslutterIdent = event.ansvarligBeslutterIdent,

    )
}

