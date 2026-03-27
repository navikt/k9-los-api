package no.nav.k9.los.ktor.helsesjekk

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.Duration
import java.time.temporal.ChronoUnit
import kotlin.system.measureTimeMillis

fun Route.helseRoute(
    path: String = "/health",
    helsetjeneste: Helsetjeneste
) {
    get(path) {
        val resultater = mutableListOf<Result>()
        val varighet = Duration.of(measureTimeMillis {
            resultater.addAll(helsetjeneste.sjekk())
        }, ChronoUnit.MILLIS)

        val (friske, ufriske) = resultater.partition { it is Healthy }
        call.respond(
            status = if (ufriske.isEmpty()) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable,
            message = mapOf(
                "duration" to varighet.toString(),
                "healthy" to friske,
                "unhealthy" to ufriske
            )
        )
    }
}
