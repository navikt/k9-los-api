package no.nav.k9.los.reservasjon

import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto

data class OppgaveIdMedOverstyringDto(
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val overstyrSjekk: Boolean = false,
    val overstyrIdent: String? = null,
    val overstyrBegrunnelse: String? = null
)