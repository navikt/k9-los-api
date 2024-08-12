package no.nav.k9.los.nyoppgavestyring.query

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import org.koin.java.KoinJavaComponent
import org.koin.ktor.ext.inject

fun Route.OppgaveQueryApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveQueryService by inject<OppgaveQueryService>()
    val pepClient by KoinJavaComponent.inject<IPepClient>(IPepClient::class.java)

    @Location("/query")
    class queryOppgave

    post { _: queryOppgave ->
        val oppgaveQuery = call.receive<OppgaveQuery>()
        requestContextService.withRequestContext(call) {
            val idToken = kotlin.coroutines.coroutineContext.idToken()
            call.respond(oppgaveQueryService.query(QueryRequest(oppgaveQuery), idToken))
        }
    }

    @Location("/query/antall")
    class queryOppgaveAntall

    post { _: queryOppgaveAntall ->
        val oppgaveQuery = call.receive<OppgaveQuery>()
        requestContextService.withRequestContext(call) {
            call.respond(oppgaveQueryService.queryForAntall(QueryRequest(oppgaveQuery, false)))
        }
    }

    @Location("/validate")
    class validateOppgave

    post { _: validateOppgave ->
        val oppgaveQuery = call.receive<OppgaveQuery>()
        requestContextService.withRequestContext(call) {
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden)
            }
            call.respond(oppgaveQueryService.validate(QueryRequest(oppgaveQuery)))
        }
    }

    @Location("/queryToFile")
    class queryOppgaveToFile

    post { _: queryOppgaveToFile ->
        val oppgaveQuery = call.receive<OppgaveQuery>()
        requestContextService.withRequestContext(call) {
            val idToken = kotlin.coroutines.coroutineContext.idToken()

            call.response.header(
                HttpHeaders.ContentDisposition,
                ContentDisposition.Attachment.withParameter(
                    ContentDisposition.Parameters.FileName, "oppgaver.csv"
                ).toString()
            )
            call.respondText(oppgaveQueryService.queryToFile(QueryRequest(oppgaveQuery), idToken))
        }
    }

    @Location("/felter")
    class hentAlleFelter

    get { _: hentAlleFelter ->
        requestContextService.withRequestContext(call) {
            call.respond(oppgaveQueryService.hentAlleFelter())
        }
    }
}