package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.NøkkeltallV3Apis() {
    val nøkkeltallService by inject<NøkkeltallService>()

    get("/dagens-tall") {
        call.respond(nøkkeltallService.dagensTall())
    }
}