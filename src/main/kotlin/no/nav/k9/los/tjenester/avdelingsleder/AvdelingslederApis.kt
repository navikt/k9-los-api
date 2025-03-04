package no.nav.k9.los.tjenester.avdelingsleder

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.AvdelingslederApis() {
    val avdelingslederTjeneste by inject<AvdelingslederTjeneste>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    get("/saksbehandlere") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(avdelingslederTjeneste.hentSaksbehandlere())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    // TODO: slett når frontend har begynt å bruke nytt endepunkt
    post("/saksbehandlere/sok") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val epost = call.receive<EpostDto>()
                call.respond(avdelingslederTjeneste.søkSaksbehandler(epost))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/legg-til") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val epost = call.receive<EpostDto>()
                call.respond(avdelingslederTjeneste.leggTilSaksbehandler(epost.epost))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/slett") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val epost = call.receive<EpostDto>()
                call.respond(avdelingslederTjeneste.slettSaksbehandler(epost.epost))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/reservasjoner") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(avdelingslederTjeneste.hentAlleAktiveReservasjonerV3())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}

