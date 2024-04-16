package no.nav.k9.los.tjenester.sse

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.server.application.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.broadcast
import kotlinx.coroutines.channels.produce
import no.nav.k9.los.utils.LosObjectMapper
import org.slf4j.LoggerFactory
import java.util.*

internal object RefreshKlienter {
    private val logger = LoggerFactory.getLogger(RefreshKlienter::class.java)

    private inline fun <reified T> ObjectMapper.asString(value: T): String = writeValueAsString(value)

    internal fun Application.sseChannel(channel: Channel<SseEvent>) = sseOperation("sseChannel") {
        produce {
            for (oppgaverOppdatertEvent in channel) {
                send(oppgaverOppdatertEvent)
            }
        }.broadcast()
    }

    internal fun initializeRefreshKlienter() = sseOperation("initializeRefreshKlienter") {
        Channel<SseEvent>(Channel.UNLIMITED)
    }

    internal suspend fun Channel<SseEvent>.sendMelding(melding: Melding) {
        sseOperationCo("sendMelding") {
            val event = SseEvent(data = LosObjectMapper.instance.asString(melding))
            send(event)
        }
    }

    internal suspend fun Channel<SseEvent>.sendOppdaterTilBehandling(uuid: UUID) =
        sendMelding(
            oppdaterTilBehandlingMelding(uuid)
        )

    internal suspend fun Channel<SseEvent>.sendOppdaterReserverte() = sendMelding(oppdaterReserverteMelding())

    internal fun oppdaterTilBehandlingMelding(uuid: UUID) = Melding(
        melding = "oppdaterTilBehandling",
        id = "$uuid"
    )

    internal fun oppdaterReserverteMelding() = Melding(
        melding = "oppdaterReserverte"
    )

    internal fun <T> sseOperation(operation: String, block: () -> T) = try {
        block()
    } catch (cause: Throwable) {
        logger.error("Feil ved $operation: ${cause.stackTraceToString()}") // Får en ThrowableProxy-Error med logback
        throw cause
    }

    private suspend fun <T> sseOperationCo(operation: String, block: suspend () -> T) = try {
        block()
    } catch (cause: Throwable) {
        logger.error("Feil ved $operation: ${cause.stackTraceToString()}") // Får en ThrowableProxy-Error med logback
        throw cause
    }
}
