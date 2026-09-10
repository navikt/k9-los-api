package no.nav.k9.los.driftsmelding

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekstUtenOmråde
import no.nav.k9.los.områdeAttributeKey
import org.koin.ktor.ext.inject
import java.util.*

fun Route.DriftsmeldingerApis() {
    val driftsmeldingTjeneste by inject<DriftsmeldingTjeneste>()

    get {
        if (harTilgangTilDriftsmeldinger(skrive = false)) {
            call.respond(driftsmeldingTjeneste.hentDriftsmeldinger())
        } else {
            call.respond(HttpStatusCode.Forbidden)
        }
    }

    post {
        if (harTilgangTilDriftsmeldinger(skrive = true)) {
            val melding = call.receive<Driftsmelding>()
            call.respond(driftsmeldingTjeneste.leggTilDriftsmelding(melding.driftsmelding))
        } else {
            call.respond(HttpStatusCode.Forbidden)
        }
    }

    post("/slett") {
        if (harTilgangTilDriftsmeldinger(skrive = true)) {
            val param = call.receive<IdDto>()
            call.respond(driftsmeldingTjeneste.slettDriftsmelding(UUID.fromString(param.id)))
        } else {
            call.respond(HttpStatusCode.Forbidden)
        }
    }

    post("/toggle") {
        if (harTilgangTilDriftsmeldinger(skrive = true)) {
            val param = call.receive<DriftsmeldingSwitch>()
            call.respond(driftsmeldingTjeneste.toggleDriftsmelding(param))
        } else {
            call.respond(HttpStatusCode.Forbidden)
        }
    }
}

private suspend fun RoutingContext.harTilgangTilDriftsmeldinger(skrive: Boolean): Boolean =
    if (call.attributes.contains(områdeAttributeKey)) {
        medBrukerkontekst { bruker ->
            if (skrive) bruker.harDriftstilgang else bruker.harBasisTilgang
        }
    } else {
        medBrukerkontekstUtenOmråde { bruker ->
            if (skrive) bruker.harDriftstilgangIEttEllerFlereOmråder else bruker.harBasisTilgangIEttEllerFlereOmråder
        }
    }
