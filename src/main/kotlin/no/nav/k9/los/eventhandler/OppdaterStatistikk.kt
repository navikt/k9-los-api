package no.nav.k9.los.eventhandler

import io.prometheus.client.Histogram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.k9.los.aksjonspunktbehandling.Metrics
import no.nav.k9.los.domene.modell.OppgaveKø
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.StatistikkRepository
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors


private val log: Logger =
    LoggerFactory.getLogger("oppdaterStatistikk")

private val tidsforbrukMetrikk = Histogram.build()
    .name("los_oppdaterStatistikk")
    .help("Tidsforbruk oppdaterStatistikk")
    .register()

fun CoroutineScope.oppdaterStatistikk(
    channel: ReceiveChannel<Boolean>,
    statistikkRepository: StatistikkRepository,
    oppgaveTjeneste: OppgaveTjeneste,
    oppgaveKøRepository: OppgaveKøRepository

) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
    try {
        for (skalOppdatereStatistikk in channel) {
            delay(500)
            val t0 = System.currentTimeMillis()
            oppgaveKøRepository.hentIkkeTaHensyn().forEach {
                refreshHentAntallOppgaver(oppgaveTjeneste, it)
            }
            statistikkRepository.hentFerdigstilteOgNyeHistorikkMedYtelsetypeSiste8Uker(refresh = true)
            tidsforbrukMetrikk.observe((System.currentTimeMillis() - t0).toDouble())
        }
    } catch (e: Exception) {
        log.error("Feil ved oppdatering av statistikk", e)
    }
}


private suspend fun refreshHentAntallOppgaver(
    oppgaveTjeneste: OppgaveTjeneste,
    oppgavekø: OppgaveKø
) {
    oppgaveTjeneste.hentAntallOppgaver(
        oppgavekøId = oppgavekø.id,
        taMedReserverte = true,
        refresh = true
    )
    oppgaveTjeneste.hentAntallOppgaver(
        oppgavekøId = oppgavekø.id,
        taMedReserverte = false,
        refresh = true
    )
}
