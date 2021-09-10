package no.nav.k9.eventhandler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import no.nav.k9.domene.modell.OppgaveKø
import no.nav.k9.domene.repository.OppgaveKøRepository
import no.nav.k9.domene.repository.StatistikkRepository
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors


private val log: Logger =
    LoggerFactory.getLogger("oppdaterStatistikk")

fun CoroutineScope.oppdaterStatistikk(
    channel: ReceiveChannel<Boolean>,
    statistikkRepository: StatistikkRepository,
    oppgaveTjeneste: OppgaveTjeneste,
    oppgaveKøRepository: OppgaveKøRepository

) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
    while (true) {
        try {
            delay(500)
            channel.receive()
            oppgaveKøRepository.hentIkkeTaHensyn().forEach {
                refreshHentAntallOppgaver(oppgaveTjeneste, it)
            }
            statistikkRepository.hentFerdigstilteOgNyeHistorikkMedYtelsetypeSiste8Uker(refresh = true)
        } catch (e: Exception) {
            log.error("Feil ved oppdatering av statistikk", e)
        }
    }
}


private fun refreshHentAntallOppgaver(
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
