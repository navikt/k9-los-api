package no.nav.k9.los.jobbplanlegger

import java.time.LocalDateTime

data class JobbStatus(
    val jobb: PlanlagtJobb,
    @Volatile var nesteKjøring: LocalDateTime,
    var erAktiv: Boolean = false
)