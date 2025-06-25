package no.nav.k9.los.nyoppgavestyring.sisteoppgaver

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import org.koin.ktor.ext.inject

fun Route.SisteOppgaverApi() {
    val sisteOppgaverTjeneste by inject<SisteOppgaverTjeneste>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()


    get({
        description = "Siste 10 oppgaver innlogget bruker har besøkt."
        response {
            HttpStatusCode.OK to { body<List<SisteOppgaverDto>>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(sisteOppgaverTjeneste.hentSisteOppgaver())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post({
        description =
            "Legge til siste oppgave i listen over oppgaver innlogget bruker har besøkt, og vil slette eldste oppgave i listen. Dersom oppgave ligger i listen fra før, vil den bli flyttet til toppen av listen."
        request { body<OppgaveNøkkelDto>() }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgaveNøkkel = call.receive<OppgaveNøkkelDto>()
                sisteOppgaverTjeneste.lagreSisteOppgave(oppgaveNøkkel)
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}