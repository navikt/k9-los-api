package no.nav.k9.los.nyoppgavestyring.pep

import no.nav.k9.los.domene.repository.OppgaveKøRepository
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import kotlin.concurrent.timer

class PepCacheOppdaterer(
    val pepCacheService: PepCacheService,
    val tidMellomKjøring: Duration = Duration.ofSeconds(1),
    val alderForOppfriskning: Duration = Duration.ofHours(23),
    val forsinketOppstart: Duration = Duration.ofMinutes(5)
) {
    private val TRÅDNAVN = "k9los-pepcache-oppdaterer"
    private val log = LoggerFactory.getLogger(PepCacheOppdaterer::class.java)

    fun start(): Timer {
        return timer(
            daemon = true,
            name = TRÅDNAVN,
            period = tidMellomKjøring.toMillis(),
            initialDelay = forsinketOppstart.toMillis()
        ) {
            try {
                pepCacheService.oppdaterCacheForOppgaverEldreEnn(alderForOppfriskning)
            } catch (e: Exception) {
                log.warn("Feil ved kjøring av PepCacheOppdaterer", e)
            }
        }
    }
}