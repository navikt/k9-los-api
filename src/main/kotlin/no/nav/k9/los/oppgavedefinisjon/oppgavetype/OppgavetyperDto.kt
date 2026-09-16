package no.nav.k9.los.oppgavedefinisjon.oppgavetype

data class OppgavetyperDto (
    val område: String,
    val oppgavetyper: Set<OppgavetypeDto>
)