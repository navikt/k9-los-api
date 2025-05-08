package no.nav.k9.los.tjenester.avdelingsleder.oppgaveko

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.tjenester.avdelingsleder.AvdelingslederTjeneste
import org.koin.ktor.ext.inject
import java.util.*

fun Route.AvdelingslederOppgavekøApis() {
    val avdelingslederTjeneste by inject<AvdelingslederTjeneste>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    get {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(avdelingslederTjeneste.hentOppgaveKøer())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(avdelingslederTjeneste.opprettOppgaveKø())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/navn") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgavekøNavnDto = call.receive<OppgavekøNavnDto>()
                call.respond(avdelingslederTjeneste.endreOppgavekøNavn(oppgavekøNavnDto))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/slett") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val (id) = call.receive<IdDto>()
                call.respond(avdelingslederTjeneste.slettOppgavekø(UUID.fromString(id)))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/hent") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val uuid = call.request.queryParameters["id"]
                call.respond(avdelingslederTjeneste.hentOppgaveKø(UUID.fromString(uuid)))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/behandlingstype") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val behandling = call.receive<BehandlingsTypeDto>()
                call.respond(avdelingslederTjeneste.endreBehandlingsTyper(behandling))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/skjermet") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val behandling = call.receive<SkjermetDto>()
                call.respond(avdelingslederTjeneste.endreSkjerming(behandling))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/ytelsetype") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val ytelse = call.receive<YtelsesTypeDto>()
                call.respond(avdelingslederTjeneste.endreYtelsesType(ytelse))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/andre-kriterier") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val kriterium = call.receive<AndreKriterierDto>()
                call.respond(avdelingslederTjeneste.endreKriterium(kriterium))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/sortering") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val sortering = call.receive<KøSorteringDto>()
                call.respond(avdelingslederTjeneste.endreKøSortering(sortering))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/sortering-tidsintervall-dato") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val sortering = call.receive<SorteringDatoDto>()
                call.respond(avdelingslederTjeneste.endreKøSorteringDato(sortering))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandler") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val saksbehandler = call.receive<SaksbehandlerOppgavekoDto>()
                call.respond(avdelingslederTjeneste.leggFjernSaksbehandlerOppgavekø(saksbehandler))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val saksbehandlere = call.receive<Array<SaksbehandlerOppgavekoDto>>()
                call.respond(avdelingslederTjeneste.leggFjernSaksbehandlereFraOppgaveKø(saksbehandlere))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/kriterier") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val kriterier = call.receive<KriteriumDto>()
                call.respond(avdelingslederTjeneste.endreKøKriterier(kriterier))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
