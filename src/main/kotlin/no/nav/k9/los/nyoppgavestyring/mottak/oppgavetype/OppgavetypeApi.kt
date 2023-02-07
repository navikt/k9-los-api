package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.OppgavetypeApi() {
    val requestContextService by inject<RequestContextService>()
    val oppgavetypeTjeneste by inject<OppgavetypeTjeneste>()
    val config by inject<Configuration>()

    post {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                val innkommendeOppgavetyperDto = call.receive<OppgavetyperDto>()

                oppgavetypeTjeneste.oppdater(innkommendeOppgavetyperDto)

                call.respond("OK")
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }
}