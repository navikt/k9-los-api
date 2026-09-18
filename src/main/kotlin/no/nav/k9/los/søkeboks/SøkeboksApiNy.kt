package no.nav.k9.los.søkeboks

import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
import org.koin.ktor.ext.inject


fun Route.SøkeboksApiNy() {
    val søkeboksTjeneste by inject<SøkeboksTjeneste>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    post(
        {
            operationId = "søkEtterOppgaver"
            summary = "Søk etter oppgaver"
            description =
                "Søk etter oppgaver og tilhørende person. Dersom input er på 9 tegn antas den som journalpostId, ved 11 tegn som fødselsnummer, og ellers som fagsaknummer."
            request {
                body<SøkRequest> { description = "Søkeord for journalpost, person eller fagsak" }
            }
            response {
                HttpStatusCode.OK to { body<SøkeresultatSammendrag>() }
                HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
            }
        }
    ) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val (søkeord) = call.receive<SøkRequest>()
                call.respond(søkeboksTjeneste.finnOppgaverSammendrag(
                    område = coroutineContext.område(),
                    idToken = coroutineContext.idToken(),
                    søkeord = søkeord
                ))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
