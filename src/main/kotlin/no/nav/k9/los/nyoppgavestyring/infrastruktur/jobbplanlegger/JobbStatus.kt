package no.nav.k9.los.nyoppgavestyring.infrastruktur.jobbplanlegger

import java.time.LocalDateTime

data class JobbStatus(
    val jobb: PlanlagtJobb,
    @Volatile var nesteKjøring: Kjøretidspunkt,
    var erAktiv: Boolean = false
) {
    fun kanKjøres(nå: LocalDateTime): Boolean {
        return !erAktiv && nesteKjøring.kanKjøres(nå)
    }
}