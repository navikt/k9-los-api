package no.nav.k9.los.tjenester.saksbehandler.saksliste

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import org.koin.ktor.ext.inject
import java.util.*

internal fun Route.SaksbehandlerOppgavekoApis() {
    val pepClient by inject<IPepClient>()
    val oppgaveKøRepository by inject<OppgaveKøRepository>()
    val requestContextService by inject<RequestContextService>()
    val sakslisteTjeneste by inject<SakslisteTjeneste>()

    // Brukes kun for gamle køer. Fjernes når V1 er sanert. Kall for nye køer: Se over
    get("/oppgaveko") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(sakslisteTjeneste.hentSaksbehandlersKøer())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    // Gjelder bare for gamla køer frem til disse saneres. Kall for nye køer: OppgaveKoApis::/{id}::GET
    get("/oppgaveko/saksbehandlere") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(oppgaveKøRepository.hentOppgavekø(UUID.fromString(call.parameters["id"])).saksbehandlere)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}

