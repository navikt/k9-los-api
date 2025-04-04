package no.nav.k9.los.tjenester.fagsak

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.koin.ktor.ext.inject

internal fun Route.FagsakApis() {
    val oppgaveTjeneste by inject<OppgaveTjeneste>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    post("/sok") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val søk = call.receive<QueryString>()
                call.respond(oppgaveTjeneste.søkFagsaker(søk.searchString))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/aktoerid-sok") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val param = call.receive<AktoerIdDto>()
                call.respond(oppgaveTjeneste.finnOppgaverBasertPåAktørId(param.aktoerId))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
