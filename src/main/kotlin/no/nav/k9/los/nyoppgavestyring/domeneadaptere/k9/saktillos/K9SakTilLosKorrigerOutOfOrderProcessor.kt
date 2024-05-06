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
    channel: ReceiveChannel<UUID>,
) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcherWithErrorHandling()) {
    for (uuid in channel) {
        k9SakTilLosHistorikkvaskTjeneste.vaskOppgaveForBehandlingUUID(uuid, 0)
    }
}