package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import no.nav.k9.los.eventhandler.asCoroutineDispatcherWithErrorHandling
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosHistorikkvaskTjeneste
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors


fun CoroutineScope.k9SakKorrigerOutOfOrderProsessor(
    k9SakTilLosHistorikkvaskTjeneste: K9SakTilLosHistorikkvaskTjeneste,
    channel: ReceiveChannel<k9SakEksternId>,
) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcherWithErrorHandling()) {
    val log = LoggerFactory.getLogger("k9SakKorrigerOutOfOrderProsessor")
    for (eksternId in channel) {
        try {
            k9SakTilLosHistorikkvaskTjeneste.vaskOppgaveForBehandlingUUID(eksternId.eksternId, 0)
        } catch (e: Exception) {
            log.error("Historikkvask k9-sak feilet for enkeltoppgave med eksternId: ${eksternId.eksternId}")
        }
    }
}

data class k9SakEksternId(
    val eksternId: UUID
)