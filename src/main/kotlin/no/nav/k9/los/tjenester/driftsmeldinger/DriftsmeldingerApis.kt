package no.nav.k9.los.tjenester.driftsmeldinger

import io.ktor.application.call
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import no.nav.k9.los.tjenester.avdelingsleder.oppgaveko.IdDto
import org.koin.ktor.ext.inject
import java.util.*

fun Route.DriftsmeldingerApis() {
    val driftsmeldingTjeneste by inject<DriftsmeldingTjeneste>()

    class driftsmelding

    get { _: driftsmelding ->
        call.respond(driftsmeldingTjeneste.hentDriftsmeldinger())
    }

    post { _: driftsmelding ->
        val melding = call.receive<Driftsmelding>()
        call.respond(driftsmeldingTjeneste.leggTilDriftsmelding(melding.driftsmelding))
    }

    @Location("/slett")
    class slettDriftsmelding

    post { _: slettDriftsmelding ->
        val param = call.receive<IdDto>()
        call.respond(driftsmeldingTjeneste.slettDriftsmelding(UUID.fromString(param.id)))
    }

    @Location("/toggle")
    class toggleDriftsmelding

    post { _: toggleDriftsmelding ->
        val param = call.receive<DriftsmeldingSwitch>()
        call.respond(driftsmeldingTjeneste.toggleDriftsmelding(param))
    }
}
