package no.nav.k9.los.lagretsok

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import org.koin.ktor.ext.inject
import java.time.LocalDateTime

data class LagretSøkRespons(
    val id: Long?,
    val lagetAv: Long,
    val område: Områder,
    val versjon: Long,
    val tittel: String,
    val beskrivelse: String,
    val sistEndret: LocalDateTime,
    val query: OppgaveQuery,
)

private fun LagretSøk.tilRespons() = LagretSøkRespons(
    id = id,
    lagetAv = lagetAv,
    område = område,
    versjon = versjon,
    tittel = tittel,
    beskrivelse = beskrivelse,
    sistEndret = sistEndret,
    query = query,
)

fun Route.LagretSøkApiNy() {
    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()
    val lagretSøkTjeneste by inject<LagretSøkTjeneste>()

    get({
        operationId = "hentLagredeSok"
        summary = "Hent lagrede søk"
        response {
            HttpStatusCode.OK to { body<List<LagretSøkRespons>>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(
                    lagretSøkTjeneste.hentAlle(
                        område = coroutineContext.område(),
                        navIdent = coroutineContext.idToken().getNavIdent(),
                        kode6 = pepClient.harTilgangTilKode6()
                    ).map(LagretSøk::tilRespons)
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("{id}", {
        operationId = "hentLagretSok"
        summary = "Hent lagret søk"
        request {
            pathParameter<Long>("id") { description = "Id til det lagrede søket" }
        }
        response {
            HttpStatusCode.OK to { body<LagretSøkRespons>() }
            HttpStatusCode.NotFound to { description = "Det lagrede søket finnes ikke" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val lagretSøk = lagretSøkTjeneste.hent(
                    område = coroutineContext.område(),
                    navIdent = coroutineContext.idToken().getNavIdent(),
                    lagretSøkId = call.parameters["id"]!!.toLong()
                )
                if (lagretSøk == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(lagretSøk.tilRespons())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("{id}/antall", {
        operationId = "hentAntallForLagretSok"
        summary = "Hent antall oppgaver for lagret søk"
        request {
            pathParameter<Long>("id") { description = "Id til det lagrede søket" }
        }
        response {
            HttpStatusCode.OK to { body<Long>() }
            HttpStatusCode.NotFound to { description = "Det lagrede søket finnes ikke" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val antall = lagretSøkTjeneste.hentAntall(
                    område = coroutineContext.område(),
                    navIdent = coroutineContext.idToken().getNavIdent(),
                    lagretSøkId = call.parameters["id"]!!.toLong()
                )
                if (antall == null) call.respond(HttpStatusCode.NotFound)
                else call.respond(antall)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("default-query", {
        operationId = "hentStandardOppgaveQuery"
        summary = "Hent standardspørring"
        response {
            HttpStatusCode.OK to { body<OppgaveQuery>() }
            HttpStatusCode.Forbidden to { description = "Brukeren er ikke oppgavestyrer" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(LagretSøk.defaultQuery(coroutineContext.område(), pepClient.harTilgangTilKode6()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("nytt", {
        operationId = "opprettLagretSok"
        summary = "Opprett lagret søk"
        request { body<NyttLagretSøkRequest> { description = "Det nye lagrede søket" } }
        response {
            HttpStatusCode.Created to { body<Long>() }
            HttpStatusCode.Forbidden to { description = "Brukeren er ikke oppgavestyrer" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(
                    HttpStatusCode.Created,
                    lagretSøkTjeneste.nytt(
                        område = coroutineContext.område(),
                        navIdent = coroutineContext.idToken().getNavIdent(),
                        kode6 = pepClient.harTilgangTilKode6(),
                        nyttLagretSøk = call.receive()
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    put("{id}/endre", {
        operationId = "endreLagretSok"
        summary = "Endre lagret søk"
        request {
            pathParameter<Long>("id") { description = "Id til det lagrede søket" }
            body<EndreLagretSøkRequest> { description = "Nye verdier for det lagrede søket" }
        }
        response {
            HttpStatusCode.OK to { body<LagretSøkRespons>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(
                    lagretSøkTjeneste.endre(
                        område = coroutineContext.område(),
                        navIdent = coroutineContext.idToken().getNavIdent(),
                        kode6 = pepClient.harTilgangTilKode6(),
                        endreLagretSøk = call.receive(),
                    ).tilRespons()
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("{id}/kopier", {
        operationId = "kopierLagretSok"
        summary = "Kopier lagret søk"
        request {
            pathParameter<Long>("id") { description = "Id til det lagrede søket som kopieres" }
            body<KopierLagretSøkRequest> { description = "Tittel for kopien" }
        }
        response {
            HttpStatusCode.OK to { body<Long>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val (tittel) = call.receive<KopierLagretSøkRequest>()
                call.respond(
                    lagretSøkTjeneste.kopier(
                        område = coroutineContext.område(),
                        navIdent = coroutineContext.idToken().getNavIdent(),
                        kode6 = pepClient.harTilgangTilKode6(),
                        lagretSøkId = call.parameters["id"]!!.toLong(),
                        tittel = tittel
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    delete("{id}/slett", {
        operationId = "slettLagretSok"
        summary = "Slett lagret søk"
        request {
            pathParameter<Long>("id") { description = "Id til det lagrede søket" }
        }
        response {
            HttpStatusCode.OK to { description = "Det lagrede søket er slettet" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
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
