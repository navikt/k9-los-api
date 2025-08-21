package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import org.koin.ktor.ext.inject
import kotlin.concurrent.thread

internal fun Route.K9SakTilLosApi() {
    val requestContextService by inject<RequestContextService>()
    val k9SakTilLosHistorikkvaskTjeneste by inject<K9SakTilLosHistorikkvaskTjeneste>()

    put("/startHistorikkvask") {
        requestContextService.withRequestContext(call) {
            thread(
                start = true,
                isDaemon = true,
                name = k9SakTilLosHistorikkvaskTjeneste.METRIKKLABEL
            ) {
                k9SakTilLosHistorikkvaskTjeneste.kjørHistorikkvask()
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    post("/nullstillHistorikkvask") {
        requestContextService.withRequestContext(call) {
            k9SakTilLosHistorikkvaskTjeneste.nullstillHistorikkvask()
            call.respond(HttpStatusCode.OK)
        }
    }

}