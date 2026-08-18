package no.nav.k9.los.søkeboks

import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.område
import org.koin.ktor.ext.inject


fun Route.SøkeboksApiNy() {
    val requestContextService by inject<RequestContextService>()
    val søkeboksTjeneste by inject<SøkeboksTjeneste>()
    val pepClient by inject<IPepClient>()

    post(
        {
            description =
                "Søk etter oppgaver og tilhørende person. Dersom input er på 9 tegn antas den som journalpostId, ved 11 tegn som fødselsnummer, og ellers som fagsaknummer."
            request {
                pathParameter<Områder>("omrade") {
                    description = "Området API-kallet gjelder for"
                    example("K9") { value = Områder.K9 }
                }
                body<SøkRequest>()
            }
            response {
                HttpStatusCode.OK to { body<SøkeresultatSammendrag>() }
            }
        }
    ) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val (søkeord) = call.receive<SøkRequest>()
                call.respond(søkeboksTjeneste.finnOppgaverSammendrag(søkeord, call.område))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
