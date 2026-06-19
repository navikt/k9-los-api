package no.nav.k9.los.reservasjon

import java.time.LocalDateTime

data class ForlengReservasjonDto(
    val reservasjonsnøkkel: String,
    val kommentar: String?,
    val nyTilDato: LocalDateTime?,
)