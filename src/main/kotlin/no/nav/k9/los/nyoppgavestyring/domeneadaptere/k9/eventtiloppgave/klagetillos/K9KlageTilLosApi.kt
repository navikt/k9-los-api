package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos

import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import org.koin.ktor.ext.inject
import kotlin.concurrent.thread

internal fun Route.K9KlageTilLosApi() {
    val requestContextService by inject<RequestContextService>()
    val k9KlageTilLosHistorikkvaskTjeneste by inject<K9KlageTilLosHistorikkvaskTjeneste>()

    put("/startHistorikkvask", {
        tags("Forvaltning")
    }) {
        requestContextService.withRequestContext(call) {
            thread(
                start = true,
                isDaemon = true,
                name = k9KlageTilLosHistorikkvaskTjeneste.METRIKKLABEL
            ) {
                k9KlageTilLosHistorikkvaskTjeneste.kjørHistorikkvask()
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }
}