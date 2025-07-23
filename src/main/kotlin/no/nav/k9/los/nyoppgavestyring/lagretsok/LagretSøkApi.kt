package no.nav.k9.los.nyoppgavestyring.lagretsok

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.get
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

fun Route.LagretSøkApi() {
    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()
    val lagretSøkTjeneste by inject<LagretSøkTjeneste>()
    val lagretSøkRepository by inject<LagretSøkRepository>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()

    get({
        response {
            HttpStatusCode.OK to { body<List<LagretSøk>>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val innloggetSaksbehandler = coroutineContext.idToken().getNavIdent().let {
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(it)
                }
                val lagredeSøk = lagretSøkRepository.hentAlle(innloggetSaksbehandler!!)
                call.respond(lagredeSøk)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("{id}", {
        request {
            pathParameter<Long>("id") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { body<LagretSøk>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val id = call.parameters["id"]!!.toLong()
                val lagretSøk = lagretSøkRepository.hent(id)
                if (lagretSøk != null) {
                    call.respond(lagretSøk)
                } else {
                    call.respond(HttpStatusCode.NotFound)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("opprett", {
        request {
            body<OpprettLagretSøk>()
        }
        response {
            HttpStatusCode.Created to { body<Long>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val request = call.receive<OpprettLagretSøk>()
                val lagretSøk = lagretSøkTjeneste.opprett(coroutineContext.idToken().getNavIdent(), request)
                call.respond(HttpStatusCode.Created, lagretSøk)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    put("{id}/endre", {
        request {
            body<EndreLagretSøk>()
        }
        response {
            HttpStatusCode.OK to { body<LagretSøk>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val endreLagretSøk = call.receive<EndreLagretSøk>()
                val lagretSøk = lagretSøkTjeneste.endre(coroutineContext.idToken().getNavIdent(), endreLagretSøk)
                call.respond(HttpStatusCode.OK, lagretSøk)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    delete("slett", {
        request {
            pathParameter<Long>("id") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { body<Unit>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val lagretSøkId = call.parameters["id"]!!.toLong()
                val navIdent = coroutineContext.idToken().getNavIdent()
                lagretSøkTjeneste.slett(navIdent, lagretSøkId)
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/antall") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val lagretSøkId = call.parameters["id"]!!
                call.respond(lagretSøkTjeneste.hentAntall(lagretSøkId.toLong()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}