package no.nav.k9.los.sisteoppgaver

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import org.koin.ktor.ext.inject

fun Route.SisteOppgaverApiNy() {
    val sisteOppgaverTjeneste by inject<SisteOppgaverTjeneste>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    get({
        operationId = "hentSisteOppgaver"
        summary = "Hent sist besøkte oppgaver"
        description = "Henter de siste 10 oppgavene innlogget bruker har besøkt."
        response {
            HttpStatusCode.OK to { body<List<SisteOppgaverDto>>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(
                    sisteOppgaverTjeneste.hentSisteOppgaver(
                        område = coroutineContext.område(),
                        idToken = coroutineContext.idToken()
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post({
        operationId = "lagreSisteOppgave"
        summary = "Lagre sist besøkte oppgave"
        description =
            "Legger en oppgave øverst i listen over oppgaver innlogget bruker har besøkt. Eldste oppgave slettes når listen er full."
        request {
            body<OppgaveNøkkelDto> { description = "Nøkkel for oppgaven som ble besøkt" }
        }
        response {
            HttpStatusCode.OK to { description = "Oppgaven er lagret" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                sisteOppgaverTjeneste.lagreSisteOppgave(
                    coroutineContext.område(),
                    idToken = coroutineContext.idToken(),
                    oppgaveNøkkelDto = call.receive()
                )
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
