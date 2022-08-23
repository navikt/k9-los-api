package no.nav.k9.domene.lager.oppgave.v3

data class Oppgavefelt(
    val id: String?,
    val oppgaveTypeId: Long,
    val dataTypeId: Long,
    val visUnderOppgaveInfo: Boolean,
    val påkrevd: Boolean
)
