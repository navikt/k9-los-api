package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.k9.los.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.FeltdefinisjonApi() {
    val requestContextService by inject<RequestContextService>()
    val feltdefinisjonTjeneste by inject<FeltdefinisjonTjeneste>()

    //TODO: definere lovlige datatyper -> tolkes_som. enum i koden

    post {
        requestContextService.withRequestContext(call) {
            val innkommendeFeltdefinisjonerDto = call.receive<FeltdefinisjonerDto>()

            feltdefinisjonTjeneste.oppdater(innkommendeFeltdefinisjonerDto)

            call.respond("OK")
        }
    }
}

