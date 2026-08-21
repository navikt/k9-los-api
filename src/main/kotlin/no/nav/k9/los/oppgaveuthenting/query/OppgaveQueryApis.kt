package no.nav.k9.los.oppgaveuthenting.query

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import org.koin.java.KoinJavaComponent
import org.koin.ktor.ext.inject

fun Route.OppgaveQueryApis() {
    val oppgaveQueryService by inject<OppgaveQueryService>()

    post("/query/antall") {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang) {
                val oppgaveQuery = call.receive<OppgaveQuery>()
                call.respond(oppgaveQueryService.queryForAntall(QueryRequest(oppgaveQuery, false, område = bruker.område)))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/validate") {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang) {
                val oppgaveQuery = call.receive<OppgaveQuery>()
                call.respond(oppgaveQueryService.validate(QueryRequest(oppgaveQuery, område = bruker.område)))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/felter") {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang) {
                call.respond(oppgaveQueryService.hentAlleFelter())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
