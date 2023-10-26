package no.nav.k9.los.tjenester.saksbehandler.oppgave

import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto

data class OppgaveIdMedOverstyring(
    val oppgaveNøkkel: OppgaveNøkkelDto,
    val overstyrSjekk: Boolean = false,
    val overstyrIdent: String? = null,
    val overstyrBegrunnelse: String? = null
)