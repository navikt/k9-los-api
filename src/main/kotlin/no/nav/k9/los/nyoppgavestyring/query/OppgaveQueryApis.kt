package no.nav.k9.los.nyoppgavestyring.query

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import org.koin.java.KoinJavaComponent
import org.koin.ktor.ext.inject

fun Route.OppgaveQueryApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveQueryService by inject<OppgaveQueryService>()
    val pepClient by KoinJavaComponent.inject<IPepClient>(IPepClient::class.java)

    post("/query") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgaveQuery = call.receive<OppgaveQuery>()
                val idToken = kotlin.coroutines.coroutineContext.idToken()
                call.respond(oppgaveQueryService.query(QueryRequest(oppgaveQuery), idToken))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/query/antall") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgaveQuery = call.receive<OppgaveQuery>()
                call.respond(oppgaveQueryService.queryForAntall(QueryRequest(oppgaveQuery, false)))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/validate") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgaveQuery = call.receive<OppgaveQuery>()
                call.respond(oppgaveQueryService.validate(QueryRequest(oppgaveQuery)))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/queryToFile") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgaveQuery = call.receive<OppgaveQuery>()
                val idToken = kotlin.coroutines.coroutineContext.idToken()
                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName, "oppgaver.csv"
                    ).toString()
                )
                call.respondText(oppgaveQueryService.queryToFile(QueryRequest(oppgaveQuery), idToken))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/felter") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(oppgaveQueryService.hentAlleFelter())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}