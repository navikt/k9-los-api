package no.nav.k9.los.driftsmelding

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.område
import org.koin.ktor.ext.inject
import java.util.UUID

fun Route.DriftsmeldingerApisNy() {
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()
    val driftsmeldingTjeneste by inject<DriftsmeldingTjeneste>()

    get({
        operationId = "hentDriftsmeldinger"
        summary = "Hent driftsmeldinger"
        response {
            HttpStatusCode.OK to { body<List<DriftsmeldingDto>>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(driftsmeldingTjeneste.hentDriftsmeldinger(coroutineContext.område()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post({
        operationId = "opprettDriftsmelding"
        summary = "Opprett driftsmelding"
        request { body<Driftsmelding>() }
        response {
            HttpStatusCode.OK to { body<DriftsmeldingDto>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler driftstilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val melding = call.receive<Driftsmelding>()
                call.respond(
                    driftsmeldingTjeneste.leggTilDriftsmelding(
                        område = coroutineContext.område(),
                        melding = melding.driftsmelding,
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/slett", {
        operationId = "slettDriftsmelding"
        summary = "Slett driftsmelding"
        request { body<IdDto>() }
        response {
            HttpStatusCode.OK to { description = "Driftsmeldingen er slettet" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler driftstilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val param = call.receive<IdDto>()
                driftsmeldingTjeneste.slettDriftsmelding(
                    område = coroutineContext.område(),
                    id = UUID.fromString(param.id),
                )
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/toggle", {
        operationId = "endreDriftsmeldingstatus"
        summary = "Aktiver eller deaktiver driftsmelding"
        request { body<DriftsmeldingSwitch>() }
        response {
            HttpStatusCode.OK to { description = "Statusen er endret" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler driftstilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                driftsmeldingTjeneste.toggleDriftsmelding(
                    område = coroutineContext.område(),
                    driftsmelding = call.receive(),
                )
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
