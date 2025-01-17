package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall

import io.ktor.http.*
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

fun Route.NøkkeltallV3Apis() {
    val nøkkeltallService by inject<NøkkeltallService>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    get("/dagens-tall") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(nøkkeltallService.dagensTall())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}