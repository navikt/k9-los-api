package no.nav.k9.los.ktor.core

import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.DefaultProbeRoutes(
        readyPath : String = Paths.DEFAULT_READY_PATH,
        alivePath : String = Paths.DEFAULT_ALIVE_PATH
) {
    get(alivePath) {
        call.respondText("ALIVE")
    }

    get(readyPath) {
        call.respondText("READY")
    }
}
