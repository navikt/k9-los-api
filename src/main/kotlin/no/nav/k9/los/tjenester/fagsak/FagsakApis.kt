package no.nav.k9.los.tjenester.fagsak

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.koin.ktor.ext.inject

internal fun Route.FagsakApis() {
    val oppgaveTjeneste by inject<OppgaveTjeneste>()
    val requestContextService by inject<RequestContextService>()

    post("/sok") {
        requestContextService.withRequestContext(call) {
            val søk = call.receive<QueryString>()
            call.respond(oppgaveTjeneste.søkFagsaker(søk.searchString))
        }
    }

    post("/aktoerid-sok") {
        requestContextService.withRequestContext(call) {
            val param = call.receive<AktoerIdDto>()
            call.respond(oppgaveTjeneste.finnOppgaverBasertPåAktørId(param.aktoerId))
        }
    }
}
