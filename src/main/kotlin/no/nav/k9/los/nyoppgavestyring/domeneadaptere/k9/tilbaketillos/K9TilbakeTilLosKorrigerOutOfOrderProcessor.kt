package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.tilbaketillos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import no.nav.k9.los.eventhandler.ChannelMetrikker
import no.nav.k9.los.eventhandler.asCoroutineDispatcherWithErrorHandling
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9tilbaketillos.K9TilbakeTilLosHistorikkvaskTjeneste
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors

fun CoroutineScope.k9tilbakeKorrigerOutOfOrderProsessor(
    k9TilbakeTilLosHistorikkvaskTjeneste: K9TilbakeTilLosHistorikkvaskTjeneste,
    channel: ReceiveChannel<k9TilbakeEksternId>,
) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcherWithErrorHandling()) {
    val log = LoggerFactory.getLogger("k9TilbakeKorrigerOutOfOrderProsessor")
    for (eksternId in channel) {
        try {
            ChannelMetrikker.time("los_k9TilbakeKorrigerOutOfOrderProsessor") {
                k9TilbakeTilLosHistorikkvaskTjeneste.vaskOppgaveForBehandlingUUID(eksternId.eksternId)
            }
        } catch (e: Exception) {
            log.error("Historikkvask k9-tilbake feilet for enkeltoppgave med eksternId: ${eksternId.eksternId}")
        }
    }
}

@JvmInline
value class k9TilbakeEksternId(
    val eksternId: UUID
)