package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import no.nav.k9.los.eventhandler.asCoroutineDispatcherWithErrorHandling
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosHistorikkvaskTjeneste
import java.util.*
import java.util.concurrent.Executors


fun CoroutineScope.k9SakKorrigerOutOfOrderProsessor(
    k9SakTilLosHistorikkvaskTjeneste: K9SakTilLosHistorikkvaskTjeneste,
    channel: ReceiveChannel<k9SakEksternId>,
) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcherWithErrorHandling()) {
    for (eksternId in channel) {
        k9SakTilLosHistorikkvaskTjeneste.vaskOppgaveForBehandlingUUID(eksternId.eksternId, 0)
    }
}

data class k9SakEksternId(
    val eksternId: UUID
)