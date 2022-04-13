package no.nav.k9.tjenester.fagsak

import io.ktor.application.call
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.k9.Configuration
import no.nav.k9.KoinProfile
import no.nav.k9.integrasjon.rest.RequestContextService
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import no.nav.k9.tjenester.saksbehandler.oppgave.SokeResultatDto
import org.koin.ktor.ext.inject

internal fun Route.FagsakApis() {
    val oppgaveTjeneste by inject<OppgaveTjeneste>()
    val configuration by inject<Configuration>()
    val requestContextService by inject<RequestContextService>()

    post("/sok") {
        if (KoinProfile.LOCAL == configuration.koinProfile()) {
            call.respond(SokeResultatDto(true, null, mutableListOf()))
        } else {
            requestContextService.withRequestContext(call) {
                val søk = call.receive<QueryString>()
                call.respond(oppgaveTjeneste.søkFagsaker(søk.searchString))
            }
        }
    }

    post("/aktoerid-sok") {
        requestContextService.withRequestContext(call) {
            val param = call.receive<AktoerIdDto>()
            call.respond(oppgaveTjeneste.finnOppgaverBasertPåAktørId(param.aktoerId))
        }
    }
}
