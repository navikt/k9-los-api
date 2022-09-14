package no.nav.k9.nyoppgavestyring.mottak.oppgavetype

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.k9.integrasjon.rest.RequestContextService
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