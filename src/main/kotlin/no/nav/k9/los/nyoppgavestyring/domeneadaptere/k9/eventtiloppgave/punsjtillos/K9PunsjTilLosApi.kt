package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.K9SakTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import org.koin.ktor.ext.inject
import kotlin.concurrent.thread

internal fun Route.K9PunsjTilLosApi() {
    val requestContextService by inject<RequestContextService>()
    val k9PunsjTilLosHistorikkvaskTjeneste by inject<K9PunsjTilLosHistorikkvaskTjeneste>()

    put("/startHistorikkvask") {
        requestContextService.withRequestContext(call) {
            thread(
                start = true,
                isDaemon = true,
                name = k9PunsjTilLosHistorikkvaskTjeneste.METRIKKLABEL
            ) {
                k9PunsjTilLosHistorikkvaskTjeneste.kjørHistorikkvask()
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }

}