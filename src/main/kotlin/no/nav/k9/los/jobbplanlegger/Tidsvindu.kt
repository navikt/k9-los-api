package no.nav.k9.los.jobbplanlegger

import java.time.LocalDateTime

data class Tidsvindu(val startTime: Int, val endTime: Int) {
    fun erInnenfor(nå: LocalDateTime): Boolean {
        val time = nå.hour
        return if (startTime <= endTime) {
            time in startTime until endTime
        } else {
            time in startTime..23 || time in 0 until endTime
        }
    }
}