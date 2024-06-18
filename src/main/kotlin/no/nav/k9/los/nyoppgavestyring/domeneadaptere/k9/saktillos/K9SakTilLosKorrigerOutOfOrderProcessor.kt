package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos

import io.prometheus.client.Histogram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import no.nav.k9.los.eventhandler.asCoroutineDispatcherWithErrorHandling
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosHistorikkvaskTjeneste
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors

private val tidsforbrukMetrikk = Histogram.build()
    .name("los-k9SakKorrigerOutOfOrderProsessor")
    .help("Tidsforbruk k9SakKorrigerOutOfOrderProsessor")
    .register()


fun CoroutineScope.k9SakKorrigerOutOfOrderProsessor(
    k9SakTilLosHistorikkvaskTjeneste: K9SakTilLosHistorikkvaskTjeneste,
    channel: ReceiveChannel<k9SakEksternId>,
) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcherWithErrorHandling()) {
    val log = LoggerFactory.getLogger("k9SakKorrigerOutOfOrderProsessor")
    for (eksternId in channel) {
        try {
            val t0 = System.currentTimeMillis()
            k9SakTilLosHistorikkvaskTjeneste.vaskOppgaveForBehandlingUUID(eksternId.eksternId)
            tidsforbrukMetrikk.observe((System.currentTimeMillis() - t0).toDouble())
        } catch (e: Exception) {
            log.error("Historikkvask k9-sak feilet for enkeltoppgave med eksternId: ${eksternId.eksternId}")
        }
    }
}

@JvmInline
value class k9SakEksternId(
    val eksternId: UUID
)