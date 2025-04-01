package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.K9SakTilLosApi() {
    val requestContextService by inject<RequestContextService>()
    val k9SakTilLosHistorikkvaskTjeneste by inject<K9SakTilLosHistorikkvaskTjeneste>()

    put("/startHistorikkvask") {
        requestContextService.withRequestContext(call) {
            k9SakTilLosHistorikkvaskTjeneste.kjørHistorikkvask()
            call.respond(HttpStatusCode.NoContent)
        }
    }

}