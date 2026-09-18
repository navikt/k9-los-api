package no.nav.k9.los.innloggetbruker

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
import org.koin.ktor.ext.inject

internal fun Route.InnloggetBrukerApiNy() {
    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()
    val innloggetBrukerTjeneste by inject<InnloggetBrukerTjeneste>()

    get({
        operationId = "hentInnloggetBruker"
        summary = "Hent innlogget bruker med tilganger for området"
        response {
            HttpStatusCode.OK to { body<InnloggetBrukerDtoNy>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            val idToken = coroutineContext.idToken()
            val område = coroutineContext.område()
            val kode6 = pepClient.harTilgangTilKode6()
            call.respond(innloggetBrukerTjeneste.hentInnloggetBruker(idToken, kode6, område))
        }
    }
}
