package no.nav.k9.los.reservasjon

import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto

data class FlyttReservasjonDto(
    val oppgaveNøkkel: OppgaveNøkkelDto?,
    val reservasjonsnøkkel: String?,
    val brukerIdent: String,
    val begrunnelse: String
)
