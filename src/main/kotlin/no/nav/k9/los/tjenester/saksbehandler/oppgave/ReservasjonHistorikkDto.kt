package no.nav.k9.los.tjenester.saksbehandler.oppgave

data class ReservasjonHistorikkDto (
    val reservasjoner: List<ReservasjonDto>,
    val oppgaveId: String
)
