package no.nav.k9.los.reservasjon

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("nav.OppgaveApis")

internal fun Route.ReservasjonAdminApi() {
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()
    val reservasjonApisTjeneste by inject<ReservasjonApisTjeneste>()

    get("/alle-reservasjoner", {
        operationId = "hentAlleAktiveReservasjoner"
        summary = "Hent alle aktive reservasjoner"
        response {
            HttpStatusCode.OK to { body<List<ReservasjonDto>>() }
            HttpStatusCode.Forbidden to { description = "Brukeren er ikke oppgavestyrer" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(reservasjonApisTjeneste.hentAlleAktiveReservasjoner(
                    område = coroutineContext.område(),
                    kode6 = pepClient.harTilgangTilKode6()
                ))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
