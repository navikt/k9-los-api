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
    val pepCacheRepository: PepCacheRepository,
    val pepService: PepCacheService
) {
    private val log = LoggerFactory.getLogger(OppgaveKøRepository::class.java)

    fun start() = runBlocking {
        launch(Executors.newSingleThreadExecutor().asCoroutineDispatcherWithErrorHandling()) {
            val tidMellomKjøring = Duration.ofMinutes(1)
            while (true) {
                oppdater()
                delay(tidMellomKjøring.toMillis())
            }
        }
    }

    fun oppdater() {
        transactionalManager.transaction { tx ->
            runBlocking {
                log.info("Starter oppfrisking av oppgaver")
                pepCacheRepository.hentEldste(100, tx).forEach { pepCache ->
                    val oppgave = oppgaveRepository.hentNyesteOppgaveForEksternId(
                        tx,
                        eksternId = pepCache.eksternId,
                        kildeområde = pepCache.kildeområde
                    )
                    pepService.hent(oppgave,tx)
                }
            }
        }
    }
}