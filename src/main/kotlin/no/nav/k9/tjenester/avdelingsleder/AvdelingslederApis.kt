package no.nav.k9.tjenester.avdelingsleder

import io.ktor.application.call
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import io.ktor.routing.post
import no.nav.k9.integrasjon.rest.RequestContextService
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveId
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.koin.ktor.ext.inject
import java.util.*

internal fun Route.AvdelingslederApis() {
    val oppgaveTjeneste by inject<OppgaveTjeneste>()
    val avdelingslederTjeneste by inject<AvdelingslederTjeneste>()
    val requestContextService by inject<RequestContextService>()

    get("/oppgaver/antall-totalt") {
        requestContextService.withRequestContext(call) {
            call.respond(oppgaveTjeneste.hentAntallOppgaverTotalt())
        }
    }

    get("/oppgaver/antall") {
        requestContextService.withRequestContext(call) {
            val uuid = call.parameters["id"]
            call.respond(oppgaveTjeneste.hentAntallOppgaver(oppgavekøId = UUID.fromString(uuid), taMedReserverte = true))
        }
    }

    get("/saksbehandlere") {
        requestContextService.withRequestContext(call) {
            call.respond(avdelingslederTjeneste.hentSaksbehandlere())
        }
    }

    post("/saksbehandlere/sok") {
        requestContextService.withRequestContext(call) {
            val epost = call.receive<EpostDto>()
            call.respond(avdelingslederTjeneste.søkSaksbehandler(epost))
        }
    }

    post("/saksbehandlere/slett") {
        requestContextService.withRequestContext(call) {
            val epost = call.receive<EpostDto>()
            call.respond(avdelingslederTjeneste.fjernSaksbehandler(epost.epost))
        }
    }

    get("/reservasjoner") {
        requestContextService.withRequestContext(call) {
            call.respond(avdelingslederTjeneste.hentAlleReservasjoner())
        }
    }

    post("/reservasjoner/opphev") {
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveId>()
            call.respond(avdelingslederTjeneste.opphevReservasjon(UUID.fromString(params.oppgaveId)))
        }
    }
}
