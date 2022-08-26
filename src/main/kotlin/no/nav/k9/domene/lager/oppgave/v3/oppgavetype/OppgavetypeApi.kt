package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.OppgavetypeApi() {
    val oppgavetypeRepository by inject<OppgavetypeRepository>()
    val requestContextService by inject<RequestContextService>()
    val transactionalManager by inject<TransactionalManager>()

    post {
        requestContextService.withRequestContext(call) {
            val oppgavetyper = call.receive<Oppgavetyper>()
            transactionalManager.transaction { tx ->
                // hent alle oppgavetyper for innkommende område
                val persisterteOppgavetyper = oppgavetypeRepository.hent(oppgavetyper.område, tx)
                // sjekk diff
                persisterteOppgavetyper.finnForskjell()
                // sett inn/fjern det som trengs
            }

            call.respond("OK")
        }
    }
}