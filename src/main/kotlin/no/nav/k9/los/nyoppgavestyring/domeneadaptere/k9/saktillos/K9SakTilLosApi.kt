package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.html.I
import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosAktivvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import org.koin.ktor.ext.inject

internal fun Route.K9SakTilLosApi() {
    val requestContextService by inject<RequestContextService>()
    val k9SakTilLosAdapterTjeneste by inject<K9SakTilLosAdapterTjeneste>()
    val k9SakTilLosHistorikkvaskTjeneste by inject<K9SakTilLosHistorikkvaskTjeneste>()
    val k9SakTilLosAktivvaskTjeneste by inject<K9SakTilLosAktivvaskTjeneste>()
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

    put("/startAktivvask") {
        requestContextService.withRequestContext(call) {
            k9SakTilLosAktivvaskTjeneste.kjørAktivvask()
            call.respond(HttpStatusCode.NoContent)
        }
    }
}