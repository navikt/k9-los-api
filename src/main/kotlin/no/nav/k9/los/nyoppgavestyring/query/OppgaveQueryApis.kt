package no.nav.k9.los.nyoppgavestyring.query

import io.ktor.http.*
import io.ktor.server.application.call
import io.ktor.server.locations.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveQueryRepository
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveIdMedOverstyring
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.koin.ktor.ext.inject
import java.util.*

fun Route.OppgaveQueryApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveQueryRepository by inject<OppgaveQueryRepository>()
    val oppgaveQueryService by inject<OppgaveQueryService>()

    @Location("/query")
    class queryOppgave

    post { _: queryOppgave ->
        val oppgaveQuery = call.receive<OppgaveQuery>()
        requestContextService.withRequestContext(call) {
            call.respond(oppgaveQueryService.query(oppgaveQuery))
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