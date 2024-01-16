package no.nav.k9.los.tjenester.saksbehandler.saksliste

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.modell.OppgaveKø
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import org.koin.ktor.ext.inject
import java.util.*

internal fun Route.SaksbehandlerOppgavekoApis() {
    val pepClient by inject<IPepClient>()
    val oppgaveKøRepository by inject<OppgaveKøRepository>()
    val requestContextService by inject<RequestContextService>()
    val sakslisteTjeneste by inject<SakslisteTjeneste>()

    @Deprecated("Brukes kun for gamle køer. Fjernes når V1 er sanert. Kall for nye køer: Se over")
    @Location("/oppgaveko")
    class getKoerForSaksbehandlerV1
    get { _: getKoerForSaksbehandlerV1 ->
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(sakslisteTjeneste.hentSaksbehandlersKøer())
            } else {
                call.respond(emptyList<OppgaveKø>())
            }
        }
    }

    @Deprecated("Gjelder bare for gamla køer frem til disse saneres. Kall for nye køer: OppgaveKoApis::/{id}::GET")
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

