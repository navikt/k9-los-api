package no.nav.k9.los.domeneadaptere.eventmottak.k9.klage

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import no.nav.k9.klage.kodeverk.Fagsystem
import no.nav.k9.klage.kodeverk.behandling.BehandlingType
import no.nav.k9.klage.kodeverk.behandling.oppgavetillos.EventHendelse
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.Aksjonspunkttilstand
import no.nav.k9.klage.typer.AktørId
import no.nav.k9.klage.typer.Periode
import no.nav.k9.los.domeneadaptere.eventmottak.k9.KodeverkDeserializer
import no.nav.k9.los.forvaltning.SENSITIVE_FIELDS
import no.nav.k9.los.forvaltning.SensitiveField
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

data class K9KlageEventDto(
    val eksternId: UUID,

    val påklagdBehandlingId: UUID?,

    val påklagdBehandlingType: BehandlingType?,
    val fagsystem: Fagsystem,

    val utenlandstilsnitt: Boolean?,

    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val behandlingstidFrist: LocalDate?,

    val saksnummer: String,
    @field:SensitiveField(SENSITIVE_FIELDS.AKTOR_ID)
    val aktørId: String,

    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    val eventTid: LocalDateTime,

    val eventHendelse: EventHendelse,
    val behandlingStatus: String,
    val behandlingSteg: String?,
    val behandlendeEnhet: String?,
    val ansvarligBeslutter: String?,
    val ansvarligSaksbehandler: String?,
    val resultatType: String?,
    val ytelseTypeKode: String,
    val behandlingTypeKode: String,

    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    val opprettetBehandling: LocalDateTime,
    val fagsakPeriode: Periode?,
    @field:SensitiveField(SENSITIVE_FIELDS.AKTOR_ID)
    val pleietrengendeAktørId: AktørId?,
    @field:SensitiveField(SENSITIVE_FIELDS.AKTOR_ID)
    val relatertPartAktørId: AktørId?,
    val aksjonspunkttilstander: List<Aksjonspunkttilstand> = emptyList(),

    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateDeserializer::class)
    val vedtaksdato: LocalDate?,

    @JsonDeserialize(using = KodeverkDeserializer::class)
    val behandlingsårsaker: List<String>? = emptyList()
)

fun K9KlageEventDto.tryggToString(): String {
    return """K9KlageEventDto(
            eksternId=$eksternId, 
            påklagdBehandlingId=$påklagdBehandlingId,
            påklagdBehandlingType=$påklagdBehandlingType,
            fagsystem=$fagsystem, 
            utenlandstilsnitt=$utenlandstilsnitt,
            behandlingstidFrist=$behandlingstidFrist,
            saksnummer='$saksnummer', 
            eventTid=$eventTid, 
            eventHendelse=$eventHendelse, 
            behandlingStatus=$behandlingStatus, 
            behandlingSteg=$behandlingSteg, 
            behandlendeEnhet=$behandlendeEnhet,
            ansvarligBeslutter=$ansvarligBeslutter,
            ansvarligSaksbehandler=$ansvarligSaksbehandler, 
            resultatType=$resultatType,
            ytelseTypeKode='$ytelseTypeKode', 
            behandlingTypeKode='$behandlingTypeKode', 
            opprettetBehandling=$opprettetBehandling,
            fagsakPeriode=$fagsakPeriode,
            aksjonspunktTilstander=$aksjonspunkttilstander,
            vedtaksdato=$vedtaksdato,
            behandlingsårsaker=$behandlingsårsaker
            )"""
        .trimMargin()
}
