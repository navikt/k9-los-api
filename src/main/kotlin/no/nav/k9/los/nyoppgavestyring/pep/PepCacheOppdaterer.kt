package no.nav.k9.los.nyoppgavestyring.pep

import no.nav.k9.los.nyoppgavestyring.jobbplanlegger.JobbMetrikker
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import kotlin.concurrent.timer

class PepCacheOppdaterer(
    val pepCacheService: PepCacheService,
    val tidMellomKjøring: Duration = Duration.ofSeconds(4),
    val alderForOppfriskning: Duration = Duration.ofHours(23),
    val forsinketOppstart: Duration = Duration.ofMinutes(5)
) {
    private val TRÅDNAVN = "k9los-pepcache-oppdaterer"
    private val log = LoggerFactory.getLogger(PepCacheOppdaterer::class.java)

    fun startOppdateringAvÅpneOgVentende(): Timer {
        return timer(
            daemon = true,
            name = TRÅDNAVN,
            period = tidMellomKjøring.toMillis(),
            initialDelay = forsinketOppstart.toMillis()
        ) {

            try {
                JobbMetrikker.time("pepcache_oppdaterer_aapne_og_venter") {
                    pepCacheService.oppdaterCacheForÅpneOgVentendeOppgaverEldreEnn(gyldighet = alderForOppfriskning)
                }
            } catch (e: Exception) {
                log.warn("Feil ved kjøring av PepCacheOppdaterer for åpne og ventene oppgaver", e)
            }
        }
    }
}