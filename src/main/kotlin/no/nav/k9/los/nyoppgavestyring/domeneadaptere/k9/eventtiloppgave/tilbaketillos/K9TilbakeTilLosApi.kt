package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos

import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import org.koin.ktor.ext.inject
import kotlin.concurrent.thread

internal fun Route.K9TilbakeTilLosApi() {
    val requestContextService by inject<RequestContextService>()
    val k9TilbakeTilLosHistorikkvaskTjeneste by inject<K9TilbakeTilLosHistorikkvaskTjeneste>()

    put("/startHistorikkvask", {
        tags("Forvaltning")
    }) {
        requestContextService.withRequestContext(call) {
            thread(
                start = true,
                isDaemon = true,
                name = k9TilbakeTilLosHistorikkvaskTjeneste.METRIKKLABEL
            ) {
                k9TilbakeTilLosHistorikkvaskTjeneste.kjørHistorikkvask()
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }

}