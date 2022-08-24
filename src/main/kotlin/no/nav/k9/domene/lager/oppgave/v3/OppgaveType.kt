package no.nav.k9.domene.lager.oppgave.v3

data class OppgaveType(
    val id: Long?,
    val eksternId: String,
    val områdeId: Long,
    val definisjonskilde: String
)

