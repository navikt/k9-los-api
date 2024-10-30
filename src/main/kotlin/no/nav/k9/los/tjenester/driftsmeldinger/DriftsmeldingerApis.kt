package no.nav.k9.los.tjenester.driftsmeldinger

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.IdDto
import org.koin.ktor.ext.inject
import java.util.*

fun Route.DriftsmeldingerApis() {
    val driftsmeldingTjeneste by inject<DriftsmeldingTjeneste>()
    val pepClient by inject<IPepClient>()

    get("/") {
        if (pepClient.harBasisTilgang()) {
            call.respond(driftsmeldingTjeneste.hentDriftsmeldinger())
        } else {
            call.respond(HttpStatusCode.Forbidden)
        }
    }

    post("/") {
        if (pepClient.harBasisTilgang()) {
            val melding = call.receive<Driftsmelding>()
            call.respond(driftsmeldingTjeneste.leggTilDriftsmelding(melding.driftsmelding))
        } else {
            call.respond(HttpStatusCode.Forbidden)
        }
    }

    post("/slett") {
        if (pepClient.harBasisTilgang()) {
            val param = call.receive<IdDto>()
            call.respond(driftsmeldingTjeneste.slettDriftsmelding(UUID.fromString(param.id)))
        } else {
            call.respond(HttpStatusCode.Forbidden)
        }
    }

    post("/toggle") {
        if (pepClient.harBasisTilgang()) {
            val param = call.receive<DriftsmeldingSwitch>()
            call.respond(driftsmeldingTjeneste.toggleDriftsmelding(param))
        } else {
            call.respond(HttpStatusCode.Forbidden)
        }
    }
}
