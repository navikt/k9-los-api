package no.nav.k9.los.nyoppgavestyring.pep

import kotlinx.coroutines.*
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.eventhandler.asCoroutineDispatcherWithErrorHandling
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors

class PepCacheOppdaterer(
    val pepCacheService: PepCacheService,
    val tidMellomKjøring: Duration = Duration.ofSeconds(1),
    val alderForOppfriskning: Duration = Duration.ofHours(23)
) {
    private val log = LoggerFactory.getLogger(OppgaveKøRepository::class.java)

    @DelicateCoroutinesApi
    fun start(): Job {
        return GlobalScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcherWithErrorHandling()) {
            while (true) {
                pepCacheService.oppdaterCacheForOppgaverEldreEnn(alderForOppfriskning)
                delay(tidMellomKjøring.toMillis())
            }
        }
    }
}