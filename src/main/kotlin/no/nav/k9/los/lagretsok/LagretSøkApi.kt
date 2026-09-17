package no.nav.k9.los.lagretsok

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.routing.get
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import org.koin.ktor.ext.inject

fun Route.LagretSøkApi() {
    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()
    val lagretSøkTjeneste by inject<LagretSøkTjeneste>()

    get({
        response {
            HttpStatusCode.OK to { body<List<LagretSøk>>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(
                    lagretSøkTjeneste.hentAlle(
                        område = coroutineContext.område(),
                        navIdent = coroutineContext.idToken().getNavIdent(),
                        kode6 = pepClient.harTilgangTilKode6()
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("{id}", {
        request {
            pathParameter<Long>("id") { required = true }
        }
        response {
            HttpStatusCode.OK to { body<LagretSøk>() }
            HttpStatusCode.NotFound to { }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val lagretSøk = lagretSøkTjeneste.hent(
                    område = coroutineContext.område(),
                    navIdent = coroutineContext.idToken().getNavIdent(),
                    lagretSøkId = call.parameters["id"]!!.toLong()
                )
                if (lagretSøk == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(lagretSøk)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("{id}/antall") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val antall = lagretSøkTjeneste.hentAntall(
                    område = coroutineContext.område(),
                    navIdent = coroutineContext.idToken().getNavIdent(),
                    lagretSøkId = call.parameters["id"]!!.toLong()
                )
                if (antall == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(antall)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("default-query", {
        response {
            HttpStatusCode.OK to { body<OppgaveQuery>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(LagretSøk.defaultQuery(
                    område = coroutineContext.område(),
                    kode6 = pepClient.harTilgangTilKode6()
                ))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("nytt", {
        request {
            body<NyttLagretSøkRequest>()
        }
        response {
            HttpStatusCode.Created to { body<Long>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val lagretSøkId = lagretSøkTjeneste.nytt(
                    område = coroutineContext.område(),
                    navIdent = coroutineContext.idToken().getNavIdent(),
                    kode6 = pepClient.harTilgangTilKode6(),
                    nyttLagretSøk = call.receive<NyttLagretSøkRequest>()
                )
                call.respond(HttpStatusCode.Created, lagretSøkId)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    put("{id}/endre", {
        request {
            body<EndreLagretSøkRequest>()
        }
        response {
            HttpStatusCode.OK to { body<LagretSøk>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val lagretSøk = lagretSøkTjeneste.endre(
                    område = coroutineContext.område(),
                    navIdent = coroutineContext.idToken().getNavIdent(),
                    kode6 = pepClient.harTilgangTilKode6(),
                    endreLagretSøk = call.receive<EndreLagretSøkRequest>()
                )
                call.respond(HttpStatusCode.OK, lagretSøk)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("{id}/kopier", {
        request {
            body<KopierLagretSøkRequest>()
        }
        response {
            HttpStatusCode.OK to { body<Long>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val (tittel) = call.receive<KopierLagretSøkRequest>()
                val nyttLagretSøkId = lagretSøkTjeneste.kopier(
                    område = coroutineContext.område(),
                    navIdent = coroutineContext.idToken().getNavIdent(),
                    kode6 = pepClient.harTilgangTilKode6(),
                    lagretSøkId = call.parameters["id"]!!.toLong(),
                    tittel = tittel
                )
                call.respond(HttpStatusCode.OK, nyttLagretSøkId)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    delete("{id}/slett", {
        request {
            pathParameter<Long>("id") { required = true }
        }
        response {
            HttpStatusCode.OK to { body<Unit>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                lagretSøkTjeneste.slett(
                    område = coroutineContext.område(),
                    navIdent = coroutineContext.idToken().getNavIdent(),
                    kode6 = pepClient.harTilgangTilKode6(),
                    lagretSøkId = call.parameters["id"]!!.toLong()
                )
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
