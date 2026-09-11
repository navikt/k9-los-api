package no.nav.k9.los.innloggetbruker

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.InnloggetBrukersOmråderApi() {
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    get {
        requestContextService.withRequestContext(call) {
            call.respond(pepClient.basisTilgangIOmråder())
        }
    }
}
