package no.nav.k9.los.tjenester.avdelingsleder.oppgaveko

import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.tjenester.avdelingsleder.AvdelingslederTjeneste
import org.koin.ktor.ext.inject
import java.util.*

fun Route.AvdelingslederOppgavekøApis() {
    val avdelingslederTjeneste by inject<AvdelingslederTjeneste>()
    val requestContextService by inject<RequestContextService>()

    get {
        requestContextService.withRequestContext(call) {
            call.respond(avdelingslederTjeneste.hentOppgaveKøer())
        }
    }

    post {
        requestContextService.withRequestContext(call) {
            call.respond(avdelingslederTjeneste.opprettOppgaveKø())
        }
    }

    post("/navn") {
        requestContextService.withRequestContext(call) {
            val uuid = call.receive<OppgavekøNavnDto>()
            call.respond(avdelingslederTjeneste.endreOppgavekøNavn(uuid))
        }
    }

    post("/slett") {
        requestContextService.withRequestContext(call) {
            val uuid = call.receive<IdDto>()
            call.respond(avdelingslederTjeneste.slettOppgavekø(UUID.fromString(uuid.id)))
        }
    }

    get("/hent") {
        requestContextService.withRequestContext(call) {
            val uuid = call.request.queryParameters["id"]
            call.respond(avdelingslederTjeneste.hentOppgaveKø(UUID.fromString(uuid)))
        }
    }

    post("/behandlingstype") {
        requestContextService.withRequestContext(call) {
            val behandling = call.receive<BehandlingsTypeDto>()
            call.respond(avdelingslederTjeneste.endreBehandlingsTyper(behandling))
        }
    }

    post("/skjermet") {
        requestContextService.withRequestContext(call) {
            val behandling = call.receive<SkjermetDto>()
            call.respond(avdelingslederTjeneste.endreSkjerming(behandling))
        }
    }

    post("/ytelsetype") {
        requestContextService.withRequestContext(call) {
            val ytelse = call.receive<YtelsesTypeDto>()
            call.respond(avdelingslederTjeneste.endreYtelsesType(ytelse))
        }
    }

    post("/andre-kriterier") {
        requestContextService.withRequestContext(call) {
            val kriterium = call.receive<AndreKriterierDto>()
            call.respond(avdelingslederTjeneste.endreKriterium(kriterium))
        }
    }

    post("/sortering") {
        requestContextService.withRequestContext(call) {
            val sortering = call.receive<KøSorteringDto>()
            call.respond(avdelingslederTjeneste.endreKøSortering(sortering))
        }
    }

    post("/sortering-tidsintervall-dato") {
        requestContextService.withRequestContext(call) {
            val sortering = call.receive<SorteringDatoDto>()
            call.respond(avdelingslederTjeneste.endreKøSorteringDato(sortering))
        }
    }

    post("/saksbehandler") {
        requestContextService.withRequestContext(call) {
            val saksbehandler = call.receive<SaksbehandlerOppgavekoDto>()
            call.respond(avdelingslederTjeneste.leggFjernSaksbehandlerOppgavekø(saksbehandler))
        }
    }

    post("/saksbehandlere") {
        requestContextService.withRequestContext(call) {
            val saksbehandlere = call.receive<Array<SaksbehandlerOppgavekoDto>>()
            call.respond(avdelingslederTjeneste.leggFjernSaksbehandlereFraOppgaveKø(saksbehandlere))
        }
    }

    post("/kriterier") {
        requestContextService.withRequestContext(call) {
            val kriterier = call.receive<KriteriumDto>()
            call.respond(avdelingslederTjeneste.endreKøKriterier(kriterier))
        }
    }
}
