package no.nav.k9.los.tjenester.saksbehandler.oppgave

data class OppgaverResultat(
    val ikkeTilgang: Boolean,
    val oppgaver: MutableList<OppgaveDto>,
    val harMerknad: Boolean = false
)
