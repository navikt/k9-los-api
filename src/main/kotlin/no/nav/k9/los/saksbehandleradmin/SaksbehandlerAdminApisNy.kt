package no.nav.k9.los.saksbehandleradmin

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
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

    get("/saksbehandlere", {
        operationId = "hentSaksbehandlereForAdministrasjon"
        summary = "Hent saksbehandlere"
        response {
            HttpStatusCode.OK to { body<List<SaksbehandlerDto>>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler tilgang til oppgavestyring" }
        }
    }) {
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

    post("/saksbehandlere/legg-til", {
        operationId = "leggTilSaksbehandler"
        summary = "Legg til saksbehandler"
        request {
            body<EpostDto> { description = "E-postadressen til saksbehandleren" }
        }
        response {
            HttpStatusCode.OK to {
                body<Unit>()
            }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler tilgang til oppgavestyring" }
        }
    }) {
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

    post("/saksbehandlere/slett", {
        operationId = "slettSaksbehandlerMedEpost"
        summary = "Slett saksbehandler med e-post"
        request {
            body<EpostDto> { description = "E-postadressen til saksbehandleren" }
        }
        response {
            HttpStatusCode.OK to {
                body<Unit>()
            }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler tilgang til oppgavestyring" }
        }
    }) {
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

    delete("/saksbehandlere/{id}", {
        operationId = "slettSaksbehandlerMedId"
        summary = "Slett saksbehandler med id"
        request {
            pathParameter<Long>("id") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                body<Unit>()
            }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler tilgang til oppgavestyring" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val id = call.parameters["id"]!!.toLong()
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
