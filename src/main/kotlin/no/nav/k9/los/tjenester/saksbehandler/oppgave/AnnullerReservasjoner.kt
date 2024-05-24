package no.nav.k9.los.tjenester.saksbehandler.oppgave

import com.fasterxml.jackson.annotation.JsonFormat
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto

data class AnnullerReservasjoner(
    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY])
    val oppgaveNøkkel: Set<OppgaveNøkkelDto>,
    val begrunnelse: String,
)
