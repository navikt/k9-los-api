package no.nav.k9.los.uttrekk

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
import org.koin.ktor.ext.inject

fun Route.UttrekkApi() {
    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()
    val uttrekkTjeneste by inject<UttrekkTjeneste>()
    val uttrekkCsvGenerator by inject<UttrekkCsvGenerator>()

    get({
        response {
            HttpStatusCode.OK to { body<List<Uttrekk>>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(
                    uttrekkTjeneste.hentAlle(
                        område = coroutineContext.område(),
                        navIdent = coroutineContext.idToken().getNavIdent()
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
            HttpStatusCode.OK to { body<Uttrekk>() }
            HttpStatusCode.NotFound to { }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val uttrekk = uttrekkTjeneste.hent(
                    område = coroutineContext.område(),
                    navIdent = coroutineContext.idToken().getNavIdent(),
                    id = call.parameters["id"]!!.toLong()
                )
                if (uttrekk == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(uttrekk)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("opprett", {
        request {
            body<OpprettUttrekk>()
        }
        response {
            HttpStatusCode.Created to { body<Long>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val uttrekkId = uttrekkTjeneste.opprett(
                    område = coroutineContext.område(),
                    navIdent = coroutineContext.idToken().getNavIdent(),
                    opprettUttrekk = call.receive<OpprettUttrekk>()
                )
                call.respond(HttpStatusCode.Created, uttrekkId)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    put("{id}/tittel", {
        request {
            body<EndreTittel>()
        }
        response {
            HttpStatusCode.OK to { }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val (tittel) = call.receive<EndreTittel>()
                uttrekkTjeneste.endreTittel(
                    område = coroutineContext.område(),
                    navIdent = coroutineContext.idToken().getNavIdent(),
                    id = call.parameters["id"]!!.toLong(),
                    tittel = tittel
                )
                call.respond(HttpStatusCode.OK)
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
            HttpStatusCode.OK to { }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                uttrekkTjeneste.slett(
                    område = coroutineContext.område(),
                    navIdent = coroutineContext.idToken().getNavIdent(),
                    id = call.parameters["id"]!!.toLong()
                )
                call.respond(HttpStatusCode.OK)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    delete("lagret-sok/{lagretSokId}", {
        request {
            pathParameter<Long>("lagretSokId") { required = true }
        }
        response {
            HttpStatusCode.OK to { body<Int>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val antallSlettet = uttrekkTjeneste.slettForLagretSøk(call.parameters["lagretSokId"]!!.toLong())
                call.respond(HttpStatusCode.OK, antallSlettet)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("{id}/csv", {
        request {
            pathParameter<Long>("id") { required = true }
        }
        response {
            HttpStatusCode.OK to { }
            HttpStatusCode.NotFound to { }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val område = coroutineContext.område()
                val navIdent = coroutineContext.idToken().getNavIdent()
                val id = call.parameters["id"]!!.toLong()

                val uttrekk = uttrekkTjeneste.hent(område, navIdent, id)
                val resultat = uttrekk?.let { uttrekkTjeneste.hentResultat(område, navIdent, id) }
                if (uttrekk == null || resultat == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@withRequestContext
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        "uttrekk-$id.csv"
                    ).toString()
                )
                call.respondText(ContentType.parse("text/csv"), HttpStatusCode.OK) {
                    uttrekkCsvGenerator.genererCsv(uttrekk.query.select, resultat)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("{id}/json", {
        request {
            pathParameter<Long>("id") { required = true }
            queryParameter<Int>("offset") { required = false }
            queryParameter<Int>("limit") { required = false }
        }
        response {
            HttpStatusCode.OK to { body<UttrekkResultatRespons>() }
            HttpStatusCode.NotFound to { }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val område = coroutineContext.område()
                val navIdent = coroutineContext.idToken().getNavIdent()
                val id = call.parameters["id"]!!.toLong()

                val uttrekk = uttrekkTjeneste.hent(område, navIdent, id)
                val resultatJson = uttrekk?.let { uttrekkTjeneste.hentResultat(område, navIdent, id) }
                if (uttrekk == null || resultatJson == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@withRequestContext
                }

                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                val alleRader = UttrekkResultatMapper.fraLagretJson(resultatJson)
                val paginerteRader = alleRader.drop(offset).let { if (limit != null) it.take(limit) else it }

                call.respond(
                    UttrekkResultatRespons(
                        kolonner = uttrekk.query.select,
                        rader = paginerteRader,
                        totaltAntall = alleRader.size,
                        offset = offset,
                        limit = limit
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
