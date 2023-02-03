package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject
import org.postgresql.util.PSQLException

internal fun Route.OppgavetypeApi() {
    val requestContextService by inject<RequestContextService>()
    val oppgavetypeTjeneste by inject<OppgavetypeTjeneste>()

    post {
        requestContextService.withRequestContext(call) {
            val innkommendeOppgavetyperDto = call.receive<OppgavetyperDto>()

            try {
                oppgavetypeTjeneste.oppdater(innkommendeOppgavetyperDto)
            } catch (e : PSQLException) {
                if (e.sqlState.equals("23503")) {
                    call.respond(HttpStatusCode.BadRequest, "Constraint Violation. Forsøker å fjerne et oppgavefelt som er i bruk i en oppgave")
                }
            }

            call.respond("OK")
        }
    }
}