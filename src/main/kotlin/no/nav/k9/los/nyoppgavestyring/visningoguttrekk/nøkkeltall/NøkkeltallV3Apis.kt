package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.query.OppgaveQueryService
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.NokkeltallTjeneste
import org.koin.ktor.ext.inject

fun Route.NokkeltallApis() {
    val nokkeltallTjeneste by inject<NokkeltallTjeneste>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()
    val nøkkeltallService by inject<NøkkeltallService>()

    get("/dagens-tall") {
        call.respond(nøkkeltallService.dagensTall())
    }
}