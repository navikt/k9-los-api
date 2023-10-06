package no.nav.k9.los.tjenester.saksbehandler.oppgave

import java.time.LocalDateTime

data class ForlengReservasjonDto(
    val oppgaveId: String,
    val kommentar: String?,
    val nyTilDato: LocalDateTime?,
)