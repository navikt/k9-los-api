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
import no.nav.k9.los.infrastruktur.kontekst.medBrukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.Brukerkontekst
import kotlin.coroutines.coroutineContext
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.område
import org.koin.ktor.ext.inject

fun Route.UttrekkApiNy() {
    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()
    val uttrekkTjeneste by inject<UttrekkTjeneste>()
    val uttrekkRepository by inject<UttrekkRepository>()
    val uttrekkCsvGenerator by inject<UttrekkCsvGenerator>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()

    get({
        description = "Hent alle uttrekk for innlogget saksbehandler."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
        response {
            HttpStatusCode.OK to { body<List<Uttrekk>>() }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                val innloggetSaksbehandler = coroutineContext.idToken().getNavIdent().let {
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(it, pepClient.erKode6Bruker(Brukerkontekst(kontekst.bruker)))
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
        description = "Hent et uttrekk."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til uttrekket"
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { body<Uttrekk>() }
            HttpStatusCode.NotFound to { }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Ugyldig uttrekk-id")
                    return@medBrukerkontekst
                }
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
        description = "Opprett et nytt uttrekk basert på et lagret søk."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            body<OpprettUttrekk> {
                description = "Lagret søk uttrekket skal baseres på, tittel og evt. avgrensning"
            }
        }
        response {
            HttpStatusCode.Created to { body<Long>() }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                val innloggetSaksbehandler = coroutineContext.idToken().getNavIdent().let {
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(it, pepClient.erKode6Bruker(Brukerkontekst(kontekst.bruker)))
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

    put("/{id}/tittel", {
        description = "Endre tittel på et uttrekk."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til uttrekket"
                required = true
            }
            body<EndreTittel> {
                description = "Ny tittel"
            }
        }
        response {
            HttpStatusCode.OK to { }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Ugyldig uttrekk-id")
                    return@medBrukerkontekst
                }
                val (tittel) = call.receive<EndreTittel>()
                try {
                    uttrekkTjeneste.endreTittel(id, tittel)
                    call.respond(HttpStatusCode.OK)
                } catch (e: IllegalArgumentException) {
                    call.respond(HttpStatusCode.NotFound, e.message ?: "Uttrekk finnes ikke")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    delete("{id}/slett", {
        description = "Slett et uttrekk."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til uttrekket"
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { }
            HttpStatusCode.NotFound to { }
            HttpStatusCode.BadRequest to { }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                try {
                    val id = call.parameters["id"]?.toLongOrNull()
                    if (id == null) {
                        call.respond(HttpStatusCode.BadRequest, "Ugyldig uttrekk-id")
                        return@medBrukerkontekst
                    }
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

    delete("lagret-sok/{lagretSokId}", {
        description = "Slett alle uttrekk knyttet til et lagret søk."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("lagretSokId") {
                description = "Id til det lagrede søket"
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { body<Int>() }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                val lagretSokId = call.parameters["lagretSokId"]?.toLongOrNull()
                if (lagretSokId == null) {
                    call.respond(HttpStatusCode.BadRequest, "Ugyldig lagretSokId")
                    return@medBrukerkontekst
                }
                val antallSlettet = uttrekkTjeneste.slettForLagretSøk(lagretSokId)
                call.respond(HttpStatusCode.OK, antallSlettet)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("{id}/csv", {
        description = "Last ned resultatet av et uttrekk som CSV."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til uttrekket"
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { }
            HttpStatusCode.NotFound to { }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Ugyldig uttrekk-id")
                    return@medBrukerkontekst
                }
                val uttrekk = uttrekkTjeneste.hent(id)

                if (uttrekk == null) {
                    call.respond(HttpStatusCode.NotFound, "Uttrekk finnes ikke")
                    return@medBrukerkontekst
                }

                val resultat = uttrekkRepository.hentResultat(id)
                if (resultat == null) {
                    call.respond(HttpStatusCode.NotFound, "Uttrekk har ingen resultat")
                    return@medBrukerkontekst
                }

                call.response.header(
                    HttpHeaders.ContentDisposition,
                    ContentDisposition.Attachment.withParameter(
                        ContentDisposition.Parameters.FileName,
                        "uttrekk-$id.csv"
                    ).toString()
                )

                call.respondText(ContentType.parse("text/csv"), HttpStatusCode.OK) {
                    uttrekkCsvGenerator.genererCsv(uttrekk.query.select,resultat)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("{id}/json", {
        description = "Hent resultatet av et uttrekk som JSON, med paginering."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til uttrekket"
                required = true
            }
            queryParameter<Int>("offset") {
                description = "Antall rader som skal hoppes over. Default er 0."
                required = false
            }
            queryParameter<Int>("limit") {
                description = "Maks antall rader som skal returneres. Dersom utelatt returneres alle rader."
                required = false
            }
        }
        response {
            HttpStatusCode.OK to { body<UttrekkResultatRespons>() }
            HttpStatusCode.NotFound to { }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val område = kontekst.område
                val id = call.parameters["id"]?.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest, "Ugyldig uttrekk-id")
                    return@medBrukerkontekst
                }
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()

                val uttrekk = uttrekkTjeneste.hent(id)

                if (uttrekk == null) {
                    call.respond(HttpStatusCode.NotFound, "Uttrekk finnes ikke")
                    return@medBrukerkontekst
                }

                val resultatJson = uttrekkRepository.hentResultat(id)
                if (resultatJson == null) {
                    call.respond(HttpStatusCode.NotFound, "Uttrekk har ingen resultat")
                    return@medBrukerkontekst
                }

                val alleRader = UttrekkResultatMapper.fraLagretJson(resultatJson)

                val paginertRader = alleRader
                    .drop(offset)
                    .let { if (limit != null) it.take(limit) else it }

                val kolonner = uttrekk.query.select

                call.respond(
                    UttrekkResultatRespons(
                        kolonner = kolonner,
                        rader = paginertRader,
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
