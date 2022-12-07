package no.nav.k9.los.tjenester.saksbehandler.oppgave

import java.time.LocalDateTime

class FlyttetReservasjonDto(
    val tidspunkt: LocalDateTime,
    val uid: String,
    val navn: String,
    val begrunnelse: String
)
