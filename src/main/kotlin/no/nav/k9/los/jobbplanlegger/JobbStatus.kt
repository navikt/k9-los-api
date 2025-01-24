package no.nav.k9.los.jobbplanlegger

import java.time.LocalDateTime

class JobbStatus(
    val jobb: PlanlagtJobb,
    val tidsvindu: Tidsvindu? = null,
    var nesteKjøring: LocalDateTime?,
    var erAktiv: Boolean = false,
)