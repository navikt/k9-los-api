package no.nav.k9.los.oppgaveuthenting.query

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import no.nav.k9.los.ManglerFlerområde
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.oppgaveuthenting.query.dto.felter.Oppgavefelter
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import org.koin.ktor.ext.inject

fun Route.OppgaveQueryApisNy() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveQueryService by inject<OppgaveQueryService>()
    val pepClient by inject<IPepClient>()

    post("antall", {
        operationId = "hentAntallOppgaverForQuery"
        summary = "Tell oppgaver for query"
        request {
            body<OppgaveQuery> { description = "Filter og utvalg for oppgavespørringen" }
        }
        response {
            HttpStatusCode.OK to { body<Long>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgaveQuery = call.receive<OppgaveQuery>()
                call.respond(
                    oppgaveQueryService.queryForAntall(
                        QueryRequest(
                            område = coroutineContext.område(),
                            oppgaveQuery = oppgaveQuery,
                            fjernReserverte = false
                        )
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("validate", {
        operationId = "validerOppgaveQuery"
        summary = "Valider filtere i query"
        request {
            body<OppgaveQuery> { description = "Oppgavespørringen som skal valideres" }
        }
        response {
            HttpStatusCode.OK to { body<Boolean>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(
                    oppgaveQueryService.validate(
                        QueryRequest(
                            område = coroutineContext.område(),
                            oppgaveQuery = call.receive()
                        )
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("felter", {
        operationId = "hentOppgavefelter"
        summary = "Hent oppgavefelter for området"
        response {
            HttpStatusCode.OK to { body<Oppgavefelter>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(@ManglerFlerområde oppgaveQueryService.hentAlleFelter())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
