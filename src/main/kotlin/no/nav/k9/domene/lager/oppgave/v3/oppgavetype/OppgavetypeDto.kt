package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

data class OppgavetypeDto (
    val navn: String,
    val oppgavefelter: Set<OppgavefeltDto>
)