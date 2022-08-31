package no.nav.k9.nyoppgavestyring.oppgavetype

data class OppgavetyperDto (
    val område: String,
    val definisjonskilde: String,
    val oppgavetyper: Set<OppgavetypeDto>
)