package no.nav.k9.los.innloggetbruker

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.koin.ktor.ext.inject

internal fun Route.InnloggetBrukersOmråderApi() {
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    get({
        operationId = "hentInnloggetBrukersOmråder"
        summary = "Hent områder med basistilgang for innlogget bruker"
        response {
            HttpStatusCode.OK to { body<Set<Områder>>() }
        }
    }) {
        requestContextService.withRequestContext(call) {
            call.respond(pepClient.basisTilgangIOmråder())
        }
    }
}
