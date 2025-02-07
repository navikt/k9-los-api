package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall

import io.ktor.http.*
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

    route("status") {
        get {
            requestContextService.withRequestContext(call) {
                if (pepClient.erOppgaveStyrer()) {
                    call.respond(nøkkeltallService.hentStatus(pepClient.harTilgangTilKode6()))
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }
    }

    route("dagens-tall") {
        post("oppdater") {
            requestContextService.withRequestContext(call) {
                if (pepClient.erOppgaveStyrer()) {
                    nøkkeltallService.oppdaterDagensTall(this)
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }

        get {
            requestContextService.withRequestContext(call) {
                if (pepClient.erOppgaveStyrer()) {
                    call.respond(nøkkeltallService.dagensTall())
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }
    }


}