package no.nav.k9.los.domeneadaptere.eventmottak.ung.tilbake

import no.nav.k9.los.domeneadaptere.eventmottak.EventHendelse
import no.nav.k9.los.forvaltning.SensitiveField
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer
import tools.jackson.databind.ser.std.ToStringSerializer
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class UngTilbakeEventDto(
    val eksternId: UUID,
    val fagsystem: String,
    val saksnummer: String,
    @field:SensitiveField
    val aktørId: String,

    val behandlingId: Long? = null, //TODO: ikke i bruk i los. Relevant for frontendURL?

    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    val eventTid: LocalDateTime,

    val eventHendelse: EventHendelse,
    val behandlingStatus: String,
    val behandlingSteg: String? = null,
    val behandlendeEnhet: String? = null,

    val ytelseTypeKode: String,
    val behandlingTypeKode: String,

    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    val opprettetBehandling: LocalDateTime,

    val aksjonspunktKoderMedStatusListe: Map<String, String> = emptyMap(),

    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val førsteFeilutbetaling: LocalDate? = null,
    val feilutbetaltBeløp: BigDecimal? = null,
    val ansvarligSaksbehandlerIdent: String? = null,
    val ansvarligBeslutterIdent: String? = null,
)

