package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import java.time.LocalDateTime

data class OverførReservasjonDto(
    val fraSaksbehandlerEpost: String,
    val tilSaksbehandlerEpost: String,
    val reservasjonsnøkkel: String,
    val reserverTil: LocalDateTime,
)
