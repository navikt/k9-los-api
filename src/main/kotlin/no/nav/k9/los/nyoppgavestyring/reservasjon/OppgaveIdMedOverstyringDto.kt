package no.nav.k9.los.nyoppgavestyring.reservasjon

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto

data class OppgaveIdMedOverstyringDto(
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val overstyrSjekk: Boolean = false,
    val overstyrIdent: String? = null,
    val overstyrBegrunnelse: String? = null
)