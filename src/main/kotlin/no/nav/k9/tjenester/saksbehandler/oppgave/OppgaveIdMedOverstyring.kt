package no.nav.k9.tjenester.saksbehandler.oppgave

data class OppgaveIdMedOverstyring(
    val oppgaveId: String,
    val overstyrSjekk: Boolean = false,
    val overstyrIdent: String? = null,
    val overstyrBegrunnelse: String? = null
)