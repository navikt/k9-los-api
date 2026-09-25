package no.nav.k9.los.reservasjon

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.område
import org.koin.ktor.ext.inject

internal fun Route.ReservasjonAdminApiNy() {
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()
    val reservasjonApisTjeneste by inject<ReservasjonApisTjeneste>()

    get("/alle-reservasjoner", {
        operationId = "hentAlleAktiveReservasjoner"
        summary = "Hent alle aktive reservasjoner"
        response {
            HttpStatusCode.OK to { body<List<ReservasjonMedOppgaverDto>>() }
            HttpStatusCode.Forbidden to { description = "Brukeren er ikke oppgavestyrer" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(
                    reservasjonApisTjeneste.hentAlleAktiveReservasjonerNy(
                        område = coroutineContext.område(),
                        kode6 = pepClient.harTilgangTilKode6()
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
