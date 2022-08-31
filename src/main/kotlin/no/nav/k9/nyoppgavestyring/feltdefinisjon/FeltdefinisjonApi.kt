package no.nav.k9.nyoppgavestyring.feltdefinisjon

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.k9.integrasjon.rest.RequestContextService
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

