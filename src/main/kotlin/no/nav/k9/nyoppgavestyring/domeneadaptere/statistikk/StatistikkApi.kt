package no.nav.k9.nyoppgavestyring.domeneadaptere.statistikk

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.k9.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.StatistikkApi() {
    val requestContextService by inject<RequestContextService>()
    val oppgavestatistikkTjeneste by inject<OppgavestatistikkTjeneste>()

    put {
        requestContextService.withRequestContext(call) {
            oppgavestatistikkTjeneste.kjør(kjørUmiddelbart = true)
            call.respond("OK")
        }
    }

    //TODO: Til test. Fjernes før prodsetting!
    delete("/slettStatistikkgrunnlag") {
        requestContextService.withRequestContext(call) {
            oppgavestatistikkTjeneste.slettStatistikkgrunnlag()
            call.respond("OK")
        }
    }

}