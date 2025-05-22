package no.nav.k9.los.nyoppgavestyring.lagretsok

import io.github.smiley4.ktorswaggerui.dsl.routing.delete
import io.github.smiley4.ktorswaggerui.dsl.routing.post
import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.github.smiley4.ktorswaggerui.dsl.routing.put
import io.ktor.client.utils.EmptyContent.status
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.core.instrument.binder.http.HttpJakartaServletRequestTags.status
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import no.nav.k9.los.nyoppgavestyring.søkeboks.SøkeboksOppgaveDto
import org.koin.ktor.ext.inject

fun Route.LagretSøkApi() {
    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()
    val lagretSøkTjeneste by inject<LagretSøkTjeneste>()
    val lagretSøkRepository by inject<LagretSøkRepository>()

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
                val lagretSøk = lagretSøkRepository.hentLagretSøk(id)
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
                val lagretSøk = lagretSøkTjeneste.opprettLagretSøk(coroutineContext.idToken().getNavIdent(), request)
                call.respond(HttpStatusCode.Created, lagretSøk)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    put("endre", {
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
                val lagretSøk = lagretSøkTjeneste.endreLagretSøk(coroutineContext.idToken().getNavIdent(), endreLagretSøk)
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
                lagretSøkTjeneste.slettLagretSøk(coroutineContext.idToken().getNavIdent(), lagretSøkId)
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}