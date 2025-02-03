package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.K9SakTilLosApi() {
    val requestContextService by inject<RequestContextService>()
    val k9SakTilLosAdapterTjeneste by inject<K9SakTilLosAdapterTjeneste>()
    val k9SakTilLosHistorikkvaskTjeneste by inject<K9SakTilLosHistorikkvaskTjeneste>()
    val config by inject<Configuration>()

    put("/startOppgaveprosessering") {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                k9SakTilLosAdapterTjeneste.kjør(kjørUmiddelbart = true)
                call.respond("OK")
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }

    put("/startHistorikkvask") {
        requestContextService.withRequestContext(call) {
            k9SakTilLosHistorikkvaskTjeneste.kjørHistorikkvask()
            call.respond(HttpStatusCode.NoContent)
        }
    }

}