package no.nav.k9.los.oppgaveuthenting.query

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import org.koin.java.KoinJavaComponent
import org.koin.ktor.ext.inject

fun Route.OppgaveQueryApisNy() {
    val oppgaveQueryService by inject<OppgaveQueryService>()

    post("/antall", {
        description = "Hent antall oppgaver som matcher en gitt spørring."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            body<OppgaveQuery> {
                description = "Spørringen det skal telles antall treff for"
            }
        }
    }) {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang()) {
                val område = bruker.område
                val oppgaveQuery = call.receive<OppgaveQuery>()
                call.respond(oppgaveQueryService.queryForAntall(QueryRequest(oppgaveQuery, false, område = område)))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/validate", {
        description = "Valider en spørring."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            body<OppgaveQuery> {
                description = "Spørringen som skal valideres"
            }
        }
    }) {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang()) {
                val område = bruker.område
                val oppgaveQuery = call.receive<OppgaveQuery>()
                call.respond(oppgaveQueryService.validate(QueryRequest(oppgaveQuery, område = område)))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/felter", {
        description = "Hent alle felter som kan brukes i en spørring."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
    }) {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang()) {
                val område = bruker.område
                call.respond(oppgaveQueryService.hentAlleFelter())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
