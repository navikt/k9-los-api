package no.nav.k9.los.nyoppgavestyring.pep

import kotlinx.coroutines.*
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.eventhandler.asCoroutineDispatcherWithErrorHandling
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.Executors

class PepCacheOppdaterer(
    val transactionalManager: TransactionalManager,
    val oppgaveRepository: OppgaveRepository,
    val pepCacheService: PepCacheService
) {
    private val log = LoggerFactory.getLogger(OppgaveKøRepository::class.java)

    @DelicateCoroutinesApi
    fun start() = GlobalScope.launch(Executors.newSingleThreadExecutor().asCoroutineDispatcherWithErrorHandling()) {
        val tidMellomKjøring = Duration.ofMinutes(1)
        while (true) {
            log.info("Oppfrisking av oppgaver")
            pepCacheService.oppdaterCacheForOppgaverEldreEnn(Duration.ofHours(23))
            delay(tidMellomKjøring.toMillis())
        }
    }
}