package no.nav.k9.domene.lager.oppgave.v3.oppgave

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.k9.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.OppgaveV3Api() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveV3Tjeneste by inject<OppgaveV3Tjeneste>()

    put {
        requestContextService.withRequestContext(call) {
            val oppgaveDto = call.receive<OppgaveDto>()
            oppgaveV3Tjeneste.oppdater(oppgaveDto)

            call.respond("OK")
        }
    }

}