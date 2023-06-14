package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import java.time.LocalDateTime

data class TaReservasjonDto(
    val saksbehandlerEpost: String,
    val reservasjonsnøkkel: String,
    val gyldigFra: LocalDateTime = LocalDateTime.now(),
    val gyldigTil: LocalDateTime
)