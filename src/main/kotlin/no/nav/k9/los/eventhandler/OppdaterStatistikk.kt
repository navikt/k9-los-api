package no.nav.k9.los.eventhandler

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.ReceiveChannel
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.domene.repository.StatistikkRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker.ChannelMetrikker
import no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker.DetaljerMetrikker
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.concurrent.Executors


private val log: Logger = LoggerFactory.getLogger("oppdaterStatistikk")

fun CoroutineScope.oppdaterStatistikk(
    channel: ReceiveChannel<Boolean>,
    configuration: Configuration,
    statistikkRepository: StatistikkRepository,
    oppgaveTjeneste: OppgaveTjeneste

) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
    try {
        for (skalOppdatereStatistikk in channel) {
            delay(500)
            OpentelemetrySpanUtil.spanSuspend("oppdaterStatistikk", emptyMap()) {
                ChannelMetrikker.timeSuspended("oppdaterStatistikk") {
                    DetaljerMetrikker.timeSuspended("oppdaterStatistikk","refreshAntallForAlleKøer") { oppgaveTjeneste.refreshAntallForAlleKøer() }
                    DetaljerMetrikker.timeSuspended("oppdaterStatistikk","siste8uker") { statistikkRepository.hentFerdigstilteOgNyeHistorikkMedYtelsetypeSiste8Uker(refresh = true) }
                }
            }
            if (configuration.koinProfile == KoinProfile.PROD) {
                delay(57_000) //redusert hyppighet i prod for å unngå å bruke for mye ressurser
            } else {
                delay(5_000)
            }
        }
    } catch (e: CancellationException) {
        throw e // Normal flyt for coroutiner
    } catch (e: Exception) {
        log.error("Feil ved oppdatering av statistikk", e)
    }
}


