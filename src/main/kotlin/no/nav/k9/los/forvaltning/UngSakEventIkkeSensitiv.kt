package no.nav.k9.los.forvaltning

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import no.nav.k9.los.domeneadaptere.ung.eventmottak.ungsak.UngSakEventDto
import no.nav.ung.kodeverk.Fagsystem
import no.nav.ung.kodeverk.hendelse.EventHendelse
import no.nav.ung.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import no.nav.ung.sak.typer.Periode
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

data class UngSakEventIkkeSensitiv(
	val eksternId: UUID,
	val fagsystem: Fagsystem,
	val saksnummer: String,
	val vedtaksdato: LocalDate?,
	@JsonSerialize(using = ToStringSerializer::class)
	@JsonDeserialize(using = LocalDateDeserializer::class)
	val behandlingstidFrist: LocalDate?,
	@JsonSerialize(using = ToStringSerializer::class)
	@JsonDeserialize(using = LocalDateTimeDeserializer::class)
	val eventTid: LocalDateTime,
	val eventHendelse: EventHendelse,
	val behandlingStatus: String,
	val behandlingSteg: String?,
	val behandlendeEnhet: String? = null,
	val resultatType: String? = null,
	val ytelseTypeKode: String,
	val behandlingTypeKode: String,
	@JsonSerialize(using = ToStringSerializer::class)
	@JsonDeserialize(using = LocalDateTimeDeserializer::class)
	val opprettetBehandling: LocalDateTime,
	@JsonSerialize(using = ToStringSerializer::class)
	@JsonDeserialize(using = LocalDateTimeDeserializer::class)
	val eldsteDatoMedEndringFraSøker: LocalDateTime?,
	val ansvarligSaksbehandlerForTotrinn: String? = null,
	val ansvarligBeslutterForTotrinn: String? = null,
	val fagsakPeriode: Periode? = null,
	val aksjonspunktTilstander: List<AksjonspunktTilstandDto> = emptyList(),
	val nyeKrav: Boolean? = null,
	val fraEndringsdialog: Boolean? = null,
	val søknadsårsaker: List<String> = emptyList(),
	val behandlingsårsaker: List<String> = emptyList(),
) {
	constructor(event: UngSakEventDto) : this(
		eksternId = event.eksternId,
		fagsystem = event.fagsystem,
		saksnummer = event.saksnummer,
		vedtaksdato = event.vedtaksdato,
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
