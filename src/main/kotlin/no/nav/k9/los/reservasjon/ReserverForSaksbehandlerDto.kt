package no.nav.k9.los.reservasjon

import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelUtenOmrådeDto

data class ReserverForSaksbehandlerDto(
    val oppgaveNøkkel: OppgaveNøkkelUtenOmrådeDto,
    val navident: String,
)
