package no.nav.k9.tjenester.saksbehandler.saksliste

import io.ktor.application.*
import io.ktor.locations.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import no.nav.k9.domene.modell.OppgaveKø
import no.nav.k9.domene.repository.OppgaveKøRepository
import no.nav.k9.integrasjon.abac.IPepClient
import no.nav.k9.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject
import java.util.*

internal fun Route.SaksbehandlerOppgavekoApis() {
    val pepClient by inject<IPepClient>()
    val oppgaveKøRepository by inject<OppgaveKøRepository>()
    val requestContextService by inject<RequestContextService>()
    val sakslisteTjeneste by inject<SakslisteTjeneste>()

    @Location("/oppgaveko")
    class getSakslister
    get { _: getSakslister ->
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(sakslisteTjeneste.hentSaksbehandlersKøer())
            } else {
                call.respond(emptyList<OppgaveKø>())
            }
        }
    }

    @Location("/oppgaveko/saksbehandlere")
    class hentSakslistensSaksbehandlere

    get { _: hentSakslistensSaksbehandlere ->
        requestContextService.withRequestContext(call) {
            call.respond(
                oppgaveKøRepository.hentOppgavekø(UUID.fromString(call.parameters["id"])).saksbehandlere
            )
        }
    }
}

