package no.nav.k9.los.nyoppgavestyring.uttrekk

import com.fasterxml.jackson.core.type.TypeReference
import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.query.dto.resultat.Oppgavefeltverdi
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

fun Route.UttrekkApi() {
    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()
    val uttrekkTjeneste by inject<UttrekkTjeneste>()
    val uttrekkRepository by inject<UttrekkRepository>()
    val uttrekkCsvGenerator by inject<UttrekkCsvGenerator>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()

    get({
        response {
            HttpStatusCode.OK to { body<List<Uttrekk>>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val innloggetSaksbehandler = coroutineContext.idToken().getNavIdent().let {
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(it)
                }
                if (innloggetSaksbehandler == null) {
                    call.respond(HttpStatusCode.Forbidden, "Innlogget bruker er ikke i saksbehandler-tabellen.")
                } else {
                    val uttrekk = uttrekkTjeneste.hentForSaksbehandler(innloggetSaksbehandler.id!!)
                    call.respond(uttrekk)
                }
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
                val innloggetSaksbehandler = coroutineContext.idToken().getNavIdent().let {
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(it)
                }
                if (innloggetSaksbehandler == null) {
                    call.respond(HttpStatusCode.Forbidden, "Innlogget bruker er ikke i saksbehandler-tabellen.")
                } else {
                    try {
                        val request = call.receive<OpprettUttrekk>()
                        val uttrekkId = uttrekkTjeneste.opprett(request, innloggetSaksbehandler.id!!)
                        call.respond(HttpStatusCode.Created, uttrekkId)
                    } catch (e: IllegalArgumentException) {
                        call.respond(HttpStatusCode.BadRequest, e.message ?: "Ugyldig forespørsel")
                    }
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
                    call.respond(HttpStatusCode.NotFound, "Uttrekk finnes ikke")
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

                call.respondText(ContentType.parse("text/csv"), HttpStatusCode.OK) {
                    uttrekkCsvGenerator.genererCsv(resultat)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("{id}/json", {
        request {
            pathParameter<Long>("id") {
                required = true
            }
            queryParameter<Int>("offset") {
                required = false
                description = "Antall rader som skal hoppes over (default: 0)"
            }
            queryParameter<Int>("limit") {
                required = false
                description = "Maks antall rader som skal returneres (default: alle)"
            }
        }
        response {
            HttpStatusCode.OK to { body<UttrekkResultatRespons>() }
            HttpStatusCode.NotFound to { }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val id = call.parameters["id"]!!.toLong()
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()

                val uttrekk = uttrekkTjeneste.hent(id)

                if (uttrekk == null) {
                    call.respond(HttpStatusCode.NotFound, "Uttrekk finnes ikke")
                    return@withRequestContext
                }

                val resultatJson = uttrekkRepository.hentResultat(id)
                if (resultatJson == null) {
                    call.respond(HttpStatusCode.NotFound, "Uttrekk har ingen resultat")
                    return@withRequestContext
                }

                val alleRader = LosObjectMapper.instance.readValue(
                    resultatJson,
                    object : TypeReference<List<List<Oppgavefeltverdi>>>() {}
                )

                val paginertRader = alleRader
                    .drop(offset)
                    .let { if (limit != null) it.take(limit) else it }

                call.respond(UttrekkResultatRespons(
                    rader = paginertRader,
                    totaltAntall = alleRader.size,
                    offset = offset,
                    limit = limit
                ))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
