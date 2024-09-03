package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.modell.Fagsystem
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
                call.respond(HttpStatusCode.NoContent)
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }

    put("resendStatistikkFraStart/{fagsystem}") {
        requestContextService.withRequestContext(call) {
            val fagsystem = call.parameters["fagsystem"]?.let { Fagsystem.valueOf(it) }
            oppgavestatistikkTjeneste.slettStatistikkgrunnlag(fagsystem)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}