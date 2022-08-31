package no.nav.k9.nyoppgavestyring.oppgavetype

data class OppgavetypeDto (
    val navn: String,
    val oppgavefelter: Set<OppgavefeltDto>
)