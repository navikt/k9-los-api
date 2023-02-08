package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject
import org.postgresql.util.PSQLException

internal fun Route.OppgavetypeApi() {
    val requestContextService by inject<RequestContextService>()
    val oppgavetypeTjeneste by inject<OppgavetypeTjeneste>()
    val config by inject<Configuration>()

    post {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                val innkommendeOppgavetyperDto = call.receive<OppgavetyperDto>()

                try {
                    oppgavetypeTjeneste.oppdater(innkommendeOppgavetyperDto)
                } catch (e: PSQLException) {
                    if (e.sqlState.equals("23503")) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Constraint Violation. Forsøker å fjerne et oppgavefelt som er i bruk i en oppgave"
                        )
                    }
                } catch (e: IllegalArgumentException) {
                    if (e.message.toString() == "Kan ikke legge til påkrevd på eksisterende oppgave uten å oppgi defaultverdi")
                        call.respond(HttpStatusCode.BadRequest, e.message.toString())
                }

                call.respond("OK")
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }
}