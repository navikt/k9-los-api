package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

data class OppgavetyperDTO (
    val område: String,
    val definisjonskilde: String,
    val oppgavetyper: Set<OppgavetypeDTO>
)