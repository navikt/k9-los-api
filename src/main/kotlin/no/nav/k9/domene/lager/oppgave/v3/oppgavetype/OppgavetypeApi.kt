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
            val innkommendeOppgavetyper = call.receive<Oppgavetyper>()
            transactionalManager.transaction { tx ->
                // hent alle oppgavetyper for innkommende område
                val persisterteOppgavetyper = oppgavetypeRepository.hent(innkommendeOppgavetyper.område, tx)
                // sjekk diff
                val (sletteListe, leggtilListe, oppdaterListe) = persisterteOppgavetyper.finnForskjell(innkommendeOppgavetyper)
                // 3 lister
                // sletteliste - for hvert element: slett først oppgavefelter, så oppgavetype
                // leggtilListe - for hvert element: insert først oppgavetype, så oppgavefelter
                // oppdaterListe - for hvert element: sjekk diff oppgavefelter og insert/delete på deltalister
            }

            call.respond("OK")
        }
    }
}