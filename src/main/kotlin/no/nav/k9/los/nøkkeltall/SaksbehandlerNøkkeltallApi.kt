package no.nav.k9.los.nøkkeltall

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte.NyeOgFerdigstilteResponse
import no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte.NyeOgFerdigstilteService
import org.koin.ktor.ext.inject

fun Route.SaksbehandlerNøkkeltallApi() {
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()
    val service by inject<NyeOgFerdigstilteService>()

    get({
        operationId = "hentSaksbehandlerNøkkeltall"
        summary = "Hent nye og ferdigstilte oppgaver de siste sju dagene"
        description = "Gruppene varierer per område. Responsen inneholder tilgjengelige grupper, datokolonner og seriene Nye og Ferdigstilte."
        request {
            queryParameter<String>("gruppe") { description = "Gruppekode fra responsens grupper. Standard ALLE." }
        }
        response {
            HttpStatusCode.OK to { body<NyeOgFerdigstilteResponse>() }
            HttpStatusCode.BadRequest to { description = "Gruppen finnes ikke i området" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang til området" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val område = coroutineContext.område()
                val gruppe = call.request.queryParameters["gruppe"] ?: "ALLE"
                if (service.grupper(område).none { it.kode == gruppe }) {
                    call.respond(HttpStatusCode.BadRequest, "Gruppen finnes ikke i området")
                } else {
                    call.respond(service.hentCachetVerdi(område, gruppe))
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
