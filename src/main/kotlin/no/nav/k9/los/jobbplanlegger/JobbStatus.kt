package no.nav.k9.los.jobbplanlegger

import java.time.LocalDateTime

class JobbStatus(
    val jobb: PlanlagtJobb,
    var nesteKjøring: LocalDateTime?,
    var erAktiv: Boolean = false,
    var jobbTidsvindu: Tidsvindu? = null
)