package no.nav.k9.los.ktor.helsesjekk

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory

class Helsetjeneste(
    private val helsesjekker: Set<HealthCheck>
) {
    private companion object {
        private val logger = LoggerFactory.getLogger(Helsetjeneste::class.java)
    }

    internal suspend fun sjekk(): List<Result> {
        val resultater = coroutineScope {
            helsesjekker.map { helsesjekk ->
                async {
                    try {
                        helsesjekk.check()
                    } catch (cause: Throwable) {
                        logger.error("Feil ved eksekvering av helsesjekk.", cause)
                        UnHealthy(
                            navn = helsesjekk.javaClass.simpleName,
                            resultat = cause.message ?: "Feil ved eksekvering av helsesjekk."
                        )
                    }
                }
            }.awaitAll()
        }
        resultater.filterIsInstance<UnHealthy>().forEach {
            logger.error("Helsesjekk feilet: ${it.resultat}")
        }
        return resultater
    }
}
