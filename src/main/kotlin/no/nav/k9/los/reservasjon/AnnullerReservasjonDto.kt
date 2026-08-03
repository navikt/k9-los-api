package no.nav.k9.los.reservasjon

import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto

data class AnnullerReservasjonDto(
    val oppgaveNøkkel: OppgaveNøkkelDto?,
    val reservasjonsnøkkel: String?
 )
