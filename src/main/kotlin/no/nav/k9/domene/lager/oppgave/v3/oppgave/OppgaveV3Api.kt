package no.nav.k9.domene.lager.oppgave.v3.oppgave

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.OppgaveV3Api() {
    val oppgaveV3Repository by inject<OppgaveV3Repository>()
    val requestContextService by inject<RequestContextService>()
    val transactionalManager by inject<TransactionalManager>()

    post {
        requestContextService.withRequestContext(call) {
            val oppgave = call.receive<OppgaveV3>()
            transactionalManager.transaction { tx ->
                // sjekk?
                // lagre oppgave
            }

            call.respond("OK")
        }
    }

}