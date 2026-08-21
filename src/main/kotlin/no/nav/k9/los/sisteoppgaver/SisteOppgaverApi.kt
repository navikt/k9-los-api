package no.nav.k9.los.sisteoppgaver

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import org.koin.ktor.ext.inject

fun Route.SisteOppgaverApi() {
    val sisteOppgaverTjeneste by inject<SisteOppgaverTjeneste>()
    val pepClient by inject<IPepClient>()


    get({
        description = "Siste 10 oppgaver innlogget bruker har besøkt."
        response {
            HttpStatusCode.OK to { body<List<SisteOppgaverDto>>() }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                call.respond(sisteOppgaverTjeneste.hentSisteOppgaver(kontekst))
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
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val oppgaveNøkkel = call.receive<OppgaveNøkkelDto>()
                sisteOppgaverTjeneste.lagreSisteOppgave(oppgaveNøkkel, kontekst)
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
