package no.nav.k9.los.nyoppgavestyring.saksbehandleradmin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonApisTjeneste
import org.koin.ktor.ext.inject
import kotlin.getValue

internal fun Route.SaksbehandlerAdminApis() {
    val requestContextService by inject<RequestContextService>()
    val saksbehandlerAdminTjeneste by inject<SaksbehandlerAdminTjeneste>()
    val pepClient by inject<IPepClient>()

    // TODO: slett når frontend har begynt å bruke nytt endepunkt i ReservasjonApis
    val reservasjonApisTjeneste by inject<ReservasjonApisTjeneste>()

    get("/saksbehandlere") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(saksbehandlerAdminTjeneste.hentSaksbehandlere())
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
                call.respond(saksbehandlerAdminTjeneste.søkSaksbehandler(epost))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/legg-til") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val epost = call.receive<EpostDto>()
                call.respond(saksbehandlerAdminTjeneste.leggTilSaksbehandler(epost.epost))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/slett") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val epost = call.receive<EpostDto>()
                call.respond(saksbehandlerAdminTjeneste.slettSaksbehandler(epost.epost))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    // TODO: slett når frontend har begynt å bruke nytt endepunkt i ReservasjonApis
    get("reservasjoner") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(reservasjonApisTjeneste.hentAlleAktiveReservasjoner())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}