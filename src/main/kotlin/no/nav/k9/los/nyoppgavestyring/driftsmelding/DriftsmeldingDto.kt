package no.nav.k9.los.nyoppgavestyring.driftsmelding

import java.time.LocalDateTime
import java.util.*

data class DriftsmeldingDto(
    val id: UUID,
    val melding: String,
    val dato: LocalDateTime,
    val aktiv: Boolean,
    val aktivert: LocalDateTime?
)

