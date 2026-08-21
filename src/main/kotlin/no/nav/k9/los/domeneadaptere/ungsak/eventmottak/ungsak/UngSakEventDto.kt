package no.nav.k9.los.domeneadaptere.ungsak.eventmottak.ungsak

import no.nav.ung.kodeverk.Fagsystem
import no.nav.ung.kodeverk.hendelse.EventHendelse
import no.nav.ung.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import no.nav.ung.sak.typer.Periode
import tools.jackson.databind.annotation.JsonDeserialize
import tools.jackson.databind.annotation.JsonSerialize
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer
import tools.jackson.databind.ser.std.ToStringSerializer
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class UngSakEventDto(
    val eksternId: UUID,
    val fagsystem: Fagsystem,
    val saksnummer: String,
    val aktørId: String,

    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    val eventTid: LocalDateTime,

    val eventHendelse: EventHendelse,
    val behandlingStatus: String,
    val behandlingSteg: String? = null,
    val behandlendeEnhet: String? = null,
    val ansvarligBeslutterForTotrinn: String? = null,
    val ansvarligSaksbehandlerForTotrinn: String? = null,
    val resultatType: String? = null,
    val ytelseTypeKode: String,
    val behandlingTypeKode: String,

    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    val eldsteDatoMedEndringFraSøker: LocalDateTime? = null,

    @JsonSerialize(using = ToStringSerializer::class)
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    val opprettetBehandling: LocalDateTime,

    val aksjonspunktKoderMedStatusListe: Map<String, String>,
    val fagsakPeriode: Periode? = null,
    val aksjonspunktTilstander: List<AksjonspunktTilstandDto> = emptyList(),
    val nyeKrav: Boolean? = null,
    val fraEndringsdialog: Boolean? = null,

    @JsonDeserialize(using = LocalDateDeserializer::class)
    val vedtaksdato: LocalDate? = null,

    val behandlingstidFrist: LocalDate? = null,
    val behandlingsårsaker: List<String> = emptyList(),
    val søknadsårsaker: List<String> = emptyList(),
)