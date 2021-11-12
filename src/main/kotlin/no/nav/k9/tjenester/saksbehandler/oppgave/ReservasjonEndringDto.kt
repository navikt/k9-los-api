package no.nav.k9.tjenester.saksbehandler.oppgave

import java.time.LocalDate

data class ReservasjonEndringDto (
    val oppgaveId: String,
    val brukerIdent: String? = null,
    val reserverTil: LocalDate? = null,
    val begrunnelse: String? = null
)
