package no.nav.k9.los.tjenester.saksbehandler.nokkeltall

import io.ktor.application.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.koin.ktor.ext.inject

fun Route.SaksbehandlerNøkkeltallApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveTjeneste by inject<OppgaveTjeneste>()

    @Location("/nokkeltall/nye-og-ferdigstilte-oppgaver")
    class getNyeOgFerdigstilteOppgaver

    get { _: getNyeOgFerdigstilteOppgaver ->
        requestContextService.withRequestContext(call) {
            call.respond(oppgaveTjeneste.hentNyeOgFerdigstilteOppgaver())
        }
    }
}
