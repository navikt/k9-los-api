package no.nav.k9.jobber

import no.nav.k9.domene.repository.StatistikkRepository
import org.slf4j.LoggerFactory
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer

class Statistikkjobb(private val statistikkRepository: StatistikkRepository) {

    private val log = LoggerFactory.getLogger(Statistikkjobb::class.java)

    fun kjørOppgaveOgBehandlingsstatistikk() {
        fixedRateTimer(
            name = "statistikk",
            daemon = true,
            initialDelay = TimeUnit.SECONDS.toMillis(10),
            period = TimeUnit.DAYS.toMillis(1)) {
            minutterLøptFørFørsteReservasjon()
            //`hvor lenge ligger journalføringsoppgaver før de håndteres`()
            //`hvor lenge ligger saker på vent i k9`()
        }
    }

    private fun minutterLøptFørFørsteReservasjon() {
        val tider = statistikkRepository.hentTiderFraOpprettetBehandlingTilFørsteReservasjon()
            .map { ChronoUnit.MINUTES.between(it.first, it.second) }

        log.info("Det tar {} minutter fra en sak kommer inn til k9 til en saksbehandler tar tak i den", tider.finnMedian())
    }

    private fun `hvor lenge ligger journalføringsoppgaver før de håndteres`() {
        log.info("Sjekker hvor lenge ligger journalføringsoppgaver før de håndteres")
    }

    private fun `hvor lenge ligger saker på vent i k9`() {
        log.info("Sjekker hvor lenge ligger saker på vent i k9")
    }

    private fun Collection<Long>.finnMedian(): Double {
        if (this.isEmpty())
            return 0.0

        val sortert = this.sorted()

        return if (sortert.size % 2 == 0) {
            ((sortert[sortert.size / 2] + sortert[sortert.size / 2 - 1]) / 2).toDouble()
        } else {
            (sortert[sortert.size / 2]).toDouble()
        }
    }

}