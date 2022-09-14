package no.nav.k9.nyoppgavestyring.mottak.oppgavetype

data class OppgavetypeDto (
    val id: String,
    val oppgavefelter: Set<OppgavefeltDto>
)