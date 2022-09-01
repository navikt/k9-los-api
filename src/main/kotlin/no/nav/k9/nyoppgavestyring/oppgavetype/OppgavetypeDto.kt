package no.nav.k9.nyoppgavestyring.oppgavetype

data class OppgavetypeDto (
    val id: String,
    val oppgavefelter: Set<OppgavefeltDto>
)