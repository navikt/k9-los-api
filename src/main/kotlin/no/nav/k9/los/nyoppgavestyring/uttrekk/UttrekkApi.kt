package no.nav.k9.los.nyoppgavestyring.uttrekk

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import org.koin.ktor.ext.inject

fun Route.UttrekkApi() {
    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()
    val uttrekkTjeneste by inject<UttrekkTjeneste>()
    val uttrekkRepository by inject<UttrekkRepository>()
    val uttrekkCsvGenerator by inject<UttrekkCsvGenerator>()
    val uttrekkCsvStreamingGenerator by inject<UttrekkCsvStreamingGenerator>()

    get({
        response {
            HttpStatusCode.OK to { body<List<Uttrekk>>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val uttrekk = uttrekkTjeneste.hentAlle()
                call.respond(uttrekk)
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
            HttpStatusCode.OK to { body<Uttrekk>() }
            HttpStatusCode.NotFound to { }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val id = call.parameters["id"]!!.toLong()
                val uttrekk = uttrekkTjeneste.hent(id)
                if (uttrekk != null) {
                    call.respond(uttrekk)
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
            body<OpprettUttrekk>()
        }
        response {
            HttpStatusCode.Created to { body<Long>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                try {
                    val request = call.receive<OpprettUttrekk>()
                    val uttrekkId = uttrekkTjeneste.opprett(request)
                    call.respond(HttpStatusCode.Created, uttrekkId)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.BadRequest, e.message ?: "Ugyldig forespørsel")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    delete("{id}/slett", {
        request {
            pathParameter<Long>("id") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { }
            HttpStatusCode.NotFound to { }
            HttpStatusCode.BadRequest to { }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                try {
                    val id = call.parameters["id"]!!.toLong()
                    uttrekkTjeneste.slett(id)
                    call.respond(HttpStatusCode.OK)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, e.message ?: "Uttrekk finnes ikke")
                } catch (e: IllegalStateException) {
                    call.respond(HttpStatusCode.BadRequest, e.message ?: "Kan ikke slette uttrekk")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("{id}/stopp", {
        request {
            pathParameter<Long>("id") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { body<Uttrekk>() }
            HttpStatusCode.NotFound to { }
            HttpStatusCode.BadRequest to { }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                try {
                    val id = call.parameters["id"]!!.toLong()
                    val uttrekk = uttrekkTjeneste.stoppUttrekk(id)
                    call.respond(HttpStatusCode.OK, uttrekk)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, e.message ?: "Uttrekk finnes ikke")
                } catch (e: IllegalStateException) {
                    call.respond(HttpStatusCode.BadRequest, e.message ?: "Kan ikke stoppe uttrekk")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("lagret-sok/{lagretSokId}", {
        request {
            pathParameter<Long>("lagretSokId") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { body<List<Uttrekk>>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val lagretSokId = call.parameters["lagretSokId"]!!.toLong()
                val uttrekk = uttrekkTjeneste.hentForLagretSok(lagretSokId)
                call.respond(uttrekk)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("{id}/csv", {
        request {
            pathParameter<Long>("id") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { }
            HttpStatusCode.NotFound to { }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val id = call.parameters["id"]!!.toLong()
                val uttrekk = uttrekkTjeneste.hent(id)

                if (uttrekk == null) {
                    call.respond(HttpStatusCode.NotFound)
                    return@withRequestContext
                }

                val resultat = uttrekkRepository.hentResultat(id)
                if (resultat == null) {
                    call.respond(HttpStatusCode.NotFound, "Uttrekk har ingen resultat")
                    return@withRequestContext
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        "uttrekk-$id.csv"
                    ).toString()
                )

                call.respondOutputStream(ContentType.parse("text/csv"), HttpStatusCode.OK) {
                    uttrekkCsvStreamingGenerator.genererCsv(resultat, this)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
