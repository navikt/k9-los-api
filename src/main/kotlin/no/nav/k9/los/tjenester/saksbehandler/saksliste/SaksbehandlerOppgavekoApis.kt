package no.nav.k9.los.tjenester.saksbehandler.saksliste

import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.modell.OppgaveKø
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
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

