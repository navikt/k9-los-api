package no.nav.k9.los.saksbehandleradmin

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.område
import org.koin.ktor.ext.inject

internal fun Route.SaksbehandlerAdminApisNy() {
    val requestContextService by inject<RequestContextService>()
    val saksbehandlerAdminTjeneste by inject<SaksbehandlerAdminTjeneste>()
    val pepClient by inject<IPepClient>()

    get("/saksbehandlere") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(saksbehandlerAdminTjeneste.hentSaksbehandlere(
                    område = coroutineContext.område(),
                    kode6 = pepClient.harTilgangTilKode6()
                ))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/legg-til") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val epost = call.receive<EpostDto>()
                call.respond(saksbehandlerAdminTjeneste.leggTilSaksbehandlerForEpost(
                    område = coroutineContext.område(),
                    kode6 = pepClient.harTilgangTilKode6(),
                    epost = epost.epost
                ))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/slett") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val epost = call.receive<EpostDto>()
                call.respond(saksbehandlerAdminTjeneste.slettSaksbehandler(
                    område = coroutineContext.område(),
                    kode6 = pepClient.harTilgangTilKode6(),
                    epost = epost.epost
                ))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/slettForId") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val id = call.receive<Long>()
                call.respond(saksbehandlerAdminTjeneste.slettSaksbehandlerForId(
                    område = coroutineContext.område(),
                    kode6 = pepClient.harTilgangTilKode6(),
                    id = id
                ))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}