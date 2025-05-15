package no.nav.k9.los.nyoppgavestyring.søkeboks

import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import org.koin.ktor.ext.inject


fun Route.SøkeboksApi() {
    val requestContextService by inject<RequestContextService>()
    val søkeboksTjeneste by inject<SøkeboksTjeneste>()
    val pepClient by inject<IPepClient>()

    post(
        {
            description =
                "Søk etter oppgaver og tilhørende person. Dersom input er på 9 tegn antas den som journalpostId, ved 11 tegn som fødselsnummer, og ellers som fagsaknummer."
            request { body<SøkQuery>() }
            response {
                HttpStatusCode.OK to { body<List<SøkeboksOppgaveDto>>() }
            }
        }
    ) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val søkQuery = call.receive<SøkQuery>()
                call.respond(søkeboksTjeneste.finnOppgaver(søkQuery.searchString, if (søkQuery.fraAktiv) listOf(Oppgavestatus.AAPEN, Oppgavestatus.VENTER) else Oppgavestatus.entries.toList()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post(
        "ny",
        {
            description =
                "Søk etter oppgaver og tilhørende person. Dersom input er på 9 tegn antas den som journalpostId, ved 11 tegn som fødselsnummer, og ellers som fagsaknummer."
            request { body<SøkQuery>() }
            response {
                HttpStatusCode.OK to { body<Søkeresultat>() }
            }
        }
    ) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val søkQuery = call.receive<SøkQuery>()
                call.respond(søkeboksTjeneste.finnOppgaverNy(søkQuery.searchString, if (søkQuery.fraAktiv) listOf(Oppgavestatus.AAPEN, Oppgavestatus.VENTER) else Oppgavestatus.entries.toList()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}