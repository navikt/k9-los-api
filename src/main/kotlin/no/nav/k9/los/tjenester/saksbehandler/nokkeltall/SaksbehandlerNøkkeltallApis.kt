package no.nav.k9.los.tjenester.saksbehandler.nokkeltall

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.koin.ktor.ext.inject

fun Route.SaksbehandlerNøkkeltallApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveTjeneste by inject<OppgaveTjeneste>()

    get("/nokkeltall/nye-og-ferdigstilte-oppgaver") {
        requestContextService.withRequestContext(call) {
            call.respond(oppgaveTjeneste.hentNyeOgFerdigstilteOppgaver())
        }
    }
}
