package no.nav.k9.domene.lager.oppgave.v3

data class OppgavefeltVerdi(
    val id: Long?,
    val oppgaveId: Long,
    val oppgaveFeltId: String,
    val verdi: String
)
