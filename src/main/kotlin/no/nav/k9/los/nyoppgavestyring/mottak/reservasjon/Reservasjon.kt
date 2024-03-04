package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import java.time.LocalDateTime

class Reservasjon(
    val id: Long? = null,
    val saksbehandlerEpost: String,
    val reservasjonsnøkkel: String,
    val gyldigTil: LocalDateTime,
)
