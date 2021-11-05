package no.nav.k9.tjenester.fagsak

import io.ktor.application.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
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

    @Location("/sok")
    class søkFagsaker
    post { _: søkFagsaker ->
        if (KoinProfile.LOCAL == configuration.koinProfile()) {
            call.respond(SokeResultatDto(true, null, mutableListOf()))
        } else {
            requestContextService.withRequestContext(call) {
                val søk = call.receive<QueryString>()
                call.respond(oppgaveTjeneste.søkFagsaker(søk.searchString))
            }
        }
    }

    @Location("/aktoerid-sok")
    class søkFagsakerMedAktørId
    post { _: søkFagsakerMedAktørId ->
        requestContextService.withRequestContext(call) {
            val param = call.receive<AktoerIdDto>()
            call.respond(oppgaveTjeneste.finnOppgaverBasertPåAktørId(param.aktoerId))
        }
    }
}
