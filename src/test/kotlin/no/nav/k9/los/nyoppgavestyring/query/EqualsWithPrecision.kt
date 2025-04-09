package no.nav.k9.los.nyoppgavestyring.query

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs

fun LocalDateTime.equalsWithPrecision(annen: LocalDateTime, errorMs: Long = 50L): Boolean {
    if (this.toLocalDate() != annen.toLocalDate()) {
        return false
    }
    return abs(ChronoUnit.MILLIS.between(this, annen)) < errorMs
}
