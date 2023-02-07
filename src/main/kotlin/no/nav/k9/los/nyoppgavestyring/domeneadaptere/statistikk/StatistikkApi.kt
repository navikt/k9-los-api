package no.nav.k9.los.nyoppgavestyring.domeneadaptere.statistikk

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.StatistikkApi() {
    val requestContextService by inject<RequestContextService>()
    val oppgavestatistikkTjeneste by inject<OppgavestatistikkTjeneste>()
    val config by inject<Configuration>()

    put {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                oppgavestatistikkTjeneste.kjør(kjørUmiddelbart = true)
                call.respond("OK")
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }

    //TODO: Til test. Fjernes før prodsetting!
    delete("/slettStatistikkgrunnlag") {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                oppgavestatistikkTjeneste.slettStatistikkgrunnlag()
                call.respond("OK")
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }
}