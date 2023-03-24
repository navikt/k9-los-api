package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import org.koin.ktor.ext.inject

internal fun Route.K9SakTilLosApi() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveV3Tjeneste by inject<OppgaveV3Tjeneste>()
    val k9SakTilLosAdapterTjeneste by inject<K9SakTilLosAdapterTjeneste>()
    val config by inject<Configuration>()

    delete("/slettOppgavedata") {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                oppgaveV3Tjeneste.destruktivSlettAvAlleOppgaveData()
                call.respond("OK")
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }

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
}