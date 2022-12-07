package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.OppgavetypeApi() {
    val requestContextService by inject<RequestContextService>()
    val oppgavetypeTjeneste by inject<OppgavetypeTjeneste>()

    post {
        requestContextService.withRequestContext(call) {
            val innkommendeOppgavetyperDto = call.receive<OppgavetyperDto>()

            oppgavetypeTjeneste.oppdater(innkommendeOppgavetyperDto)

            call.respond("OK")
        }
    }
}