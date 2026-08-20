package no.nav.k9.los.driftsmelding

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.kontekst.medBrukerkontekstUtenOmråde
import org.koin.ktor.ext.inject
import java.util.*

fun Route.DriftsmeldingerApis() {
    val pepClient by inject<IPepClient>()
    val driftsmeldingTjeneste by inject<DriftsmeldingTjeneste>()

    get {
        medBrukerkontekstUtenOmråde { kontekst ->
            // Driftsmeldinger er globale (se Gruppeoppsett), så basistilgang i minst ett område er tilstrekkelig
            if (pepClient.harBasisTilgangIEttEllerFlereOmråder(kontekst)) {
                call.respond(driftsmeldingTjeneste.hentDriftsmeldinger())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post {
        medBrukerkontekstUtenOmråde { kontekst ->
            if (pepClient.kanLeggeUtDriftsmelding(kontekst)) {
                val melding = call.receive<Driftsmelding>()
                call.respond(driftsmeldingTjeneste.leggTilDriftsmelding(melding.driftsmelding))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/slett") {
        medBrukerkontekstUtenOmråde { kontekst ->
            if (pepClient.kanLeggeUtDriftsmelding(kontekst)) {
                val param = call.receive<IdDto>()
                call.respond(driftsmeldingTjeneste.slettDriftsmelding(UUID.fromString(param.id)))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/toggle") {
        medBrukerkontekstUtenOmråde { kontekst ->
            if (pepClient.kanLeggeUtDriftsmelding(kontekst)) {
                val param = call.receive<DriftsmeldingSwitch>()
                call.respond(driftsmeldingTjeneste.toggleDriftsmelding(param))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
