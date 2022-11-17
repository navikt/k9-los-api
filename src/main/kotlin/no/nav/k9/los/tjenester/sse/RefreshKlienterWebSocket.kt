package no.nav.k9.los.tjenester.sse

import io.ktor.http.cio.websocket.*
import io.ktor.routing.*
import io.ktor.websocket.*
import kotlinx.coroutines.channels.BroadcastChannel
import no.nav.k9.los.tjenester.sse.RefreshKlienter.sseOperation
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("no.nav.RefreshKlienterWebSocket")

internal fun Route.RefreshKlienterWebSocket(sseChannel: BroadcastChannel<SseEvent>) {
    webSocket("/ws") {
        logger.info("WebSocket opened.")
        val events = sseOperation("openSubscription") {
            sseChannel.openSubscription()
        }

        try {
            for (event in events) {
                for (dataLine in event.data.lines()) {
                    outgoing.send(Frame.Text(dataLine))
                }
            }
        } finally {
            events.cancel()
            logger.info("WebSocket closed. CloseReason=[${closeReason.await()}], ClosedForSend=[${outgoing.isClosedForSend}], ClosedForReceive=[${incoming.isClosedForReceive}]")
        }
    }
}
