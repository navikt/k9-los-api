package no.nav.k9.los.websocket

import io.ktor.server.application.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import io.ktor.server.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import no.nav.k9.los.tjenester.sse.Melding
import no.nav.k9.los.tjenester.sse.RefreshKlienter.initializeRefreshKlienter
import no.nav.k9.los.tjenester.sse.RefreshKlienter.oppdaterReserverteMelding
import no.nav.k9.los.tjenester.sse.RefreshKlienter.oppdaterTilBehandlingMelding
import no.nav.k9.los.tjenester.sse.RefreshKlienter.sendMelding
import no.nav.k9.los.tjenester.sse.RefreshKlienterWebSocket
import no.nav.k9.los.tjenester.sse.SseEvent
import org.json.JSONObject
import org.junit.jupiter.api.Test
import java.util.*
import kotlin.test.assertTrue

class WebSocketTest {

    @Test
    fun `Sendte refresh meldinger over websocket`() {

        val refreshKlienter = initializeRefreshKlienter()
        val meldinger = genererRandomMeldinger()
        val mottatteMeldinger = mutableListOf<Melding>()

        withTestApplication {
            application.websocketTestApp(refreshKlienter)

            handleWebSocketConversation("/ws") { incoming, _ ->
                GlobalScope.launch {
                    for (melding in meldinger) {
                        refreshKlienter.sendMelding(melding)
                        println("Sendt $melding")
                        delay(50L)
                    }
                }

                for (frame in incoming) {
                    mottatteMeldinger.add(frame.somMelding().also {
                        println("Mottatt $it")
                    })
                    if (mottatteMeldinger.size == AntallMeldinger) {
                        break
                    }
                }
                assertTrue(mottatteMeldinger.containsAll(meldinger))
            }
        }
    }

    private companion object {
        private const val AntallMeldinger = 100

        private fun Frame.somMelding(): Melding {
            val frameText = (this as Frame.Text).readText()
            val json = JSONObject(frameText)
            return when (json.has("id") && !json.isNull("id")) {
                true -> oppdaterTilBehandlingMelding(UUID.fromString(json.getString("id")))
                false -> oppdaterReserverteMelding()
            }
        }

        private fun genererRandomMeldinger() = (1..AntallMeldinger).map {
            when ((0..1).random()) {
                0 -> oppdaterTilBehandlingMelding(UUID.randomUUID())
                else -> oppdaterReserverteMelding()
            }
        }

        private fun Application.websocketTestApp(refreshKlienter: Channel<SseEvent>) {
            install(WebSockets)
            /*routing {
                RefreshKlienterWebSocket(
                    sseChannel = refreshKlienter.broadcast()
                )
            }*/
        }
    }
}

