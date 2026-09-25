package no.nav.k9.los.uttrekk

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.ContentDisposition
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.Avgrensning
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import org.koin.ktor.ext.inject
import java.time.LocalDateTime

data class UttrekkRespons(
    val id: Long?,
    val område: Områder,
    val opprettetTidspunkt: LocalDateTime,
    val status: UttrekkStatus,
    val tittel: String,
    val query: OppgaveQuery,
    val lagetAv: Long,
    val lagretSøkId: Long?,
    val limit: Int?,
    val offset: Int?,
    val feilmelding: String?,
    val startetTidspunkt: LocalDateTime?,
    val fullførtTidspunkt: LocalDateTime?,
    val antall: Int?,
    val avgrensning: Avgrensning?,
)

private fun Uttrekk.tilRespons() = UttrekkRespons(
    id = id,
    område = område,
    opprettetTidspunkt = opprettetTidspunkt,
    status = status,
    tittel = tittel,
    query = query,
    lagetAv = lagetAv,
    lagretSøkId = lagretSøkId,
    limit = limit,
    offset = offset,
    feilmelding = feilmelding,
    startetTidspunkt = startetTidspunkt,
    fullførtTidspunkt = fullførtTidspunkt,
    antall = antall,
    avgrensning = avgrensning,
)

fun Route.UttrekkApiNy() {
    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()
    val uttrekkTjeneste by inject<UttrekkTjeneste>()
    val uttrekkCsvGenerator by inject<UttrekkCsvGenerator>()

    get({
        operationId = "hentUttrekk"
        summary = "Hent uttrekk"
        response {
            HttpStatusCode.OK to { body<List<UttrekkRespons>>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(
                    uttrekkTjeneste.hentAlle(
                        område = coroutineContext.område(),
                        navIdent = coroutineContext.idToken().getNavIdent()
                    ).map(Uttrekk::tilRespons)
                )
            } else call.respond(HttpStatusCode.Forbidden)
        }
    }

    get("{id}", {
        operationId = "hentUttrekkMedId"
        summary = "Hent uttrekk"
        request { pathParameter<Long>("id") { description = "Id til uttrekket" } }
        response {
            HttpStatusCode.OK to { body<UttrekkRespons>() }
            HttpStatusCode.NotFound to { description = "Uttrekket finnes ikke" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val uttrekk = uttrekkTjeneste.hent(
                    område = coroutineContext.område(),
                    navIdent = coroutineContext.idToken().getNavIdent(),
                    id = call.parameters["id"]!!.toLong()
                )
                if (uttrekk == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(uttrekk.tilRespons())
            } else call.respond(HttpStatusCode.Forbidden)
        }
    }

    post("opprett", {
        operationId = "opprettUttrekk"
        summary = "Opprett uttrekk"
        request { body<OpprettUttrekk> { description = "Innstillinger for uttrekket" } }
        response {
            HttpStatusCode.Created to { body<Long>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(
                    HttpStatusCode.Created,
                    uttrekkTjeneste.opprett(
                        område = coroutineContext.område(),
                        navIdent = coroutineContext.idToken().getNavIdent(),
                        kode6 = pepClient.harTilgangTilKode6(),
                        opprettUttrekk = call.receive()
                    )
                )
            } else call.respond(HttpStatusCode.Forbidden)
        }
    }

    put("{id}/tittel", {
        operationId = "endreTittelPåUttrekk"
        summary = "Endre tittel på uttrekk"
        request {
            pathParameter<Long>("id") { description = "Id til uttrekket" }
            body<EndreTittel> { description = "Ny tittel" }
        }
        response {
            HttpStatusCode.OK to { description = "Tittelen er endret" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
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
            } else call.respond(HttpStatusCode.Forbidden)
        }
    }

    delete("{id}/slett", {
        operationId = "slettUttrekk"
        summary = "Slett uttrekk"
        request { pathParameter<Long>("id") { description = "Id til uttrekket" } }
        response {
            HttpStatusCode.OK to { description = "Uttrekket er slettet" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
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
            } else call.respond(HttpStatusCode.Forbidden)
        }
    }

    delete("lagret-sok/{lagretSokId}", {
        operationId = "slettUttrekkForLagretSøk"
        summary = "Slett uttrekk for lagret søk"
        request { pathParameter<Long>("lagretSokId") { description = "Id til det lagrede søket" } }
        response {
            HttpStatusCode.OK to { body<Int>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(
                    uttrekkTjeneste.slettForLagretSøk(
                        område = coroutineContext.område(),
                        navIdent = coroutineContext.idToken().getNavIdent(),
                        lagretSøkId = call.parameters["lagretSokId"]!!.toLong()
                    )
                )
            } else call.respond(HttpStatusCode.Forbidden)
        }
    }

    get("{id}/csv", {
        operationId = "lastNedUttrekkSomCsv"
        summary = "Last ned uttrekk som CSV"
        request { pathParameter<Long>("id") { description = "Id til uttrekket" } }
        response {
            HttpStatusCode.OK to { body<String>() }
            HttpStatusCode.NotFound to { description = "Uttrekket eller resultatet finnes ikke" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
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
            } else call.respond(HttpStatusCode.Forbidden)
        }
    }

    get("{id}/json", {
        operationId = "hentUttrekksresultatSomJson"
        summary = "Hent uttrekksresultat som JSON"
        request {
            pathParameter<Long>("id") { description = "Id til uttrekket" }
            queryParameter<Int>("offset") {
                description = "Antall rader som hoppes over. Standard er 0."
                required = false
            }
            queryParameter<Int>("limit") {
                description = "Maksimalt antall rader som returneres"
                required = false
            }
        }
        response {
            HttpStatusCode.OK to { body<UttrekkResultatRespons>() }
            HttpStatusCode.NotFound to { description = "Uttrekket eller resultatet finnes ikke" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
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
            } else call.respond(HttpStatusCode.Forbidden)
        }
    }
}
