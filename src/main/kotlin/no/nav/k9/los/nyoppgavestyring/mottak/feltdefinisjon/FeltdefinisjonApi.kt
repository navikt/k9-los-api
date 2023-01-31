package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.FeltdefinisjonApi() {
    val requestContextService by inject<RequestContextService>()
    val feltdefinisjonTjeneste by inject<FeltdefinisjonTjeneste>()
    val config by inject<Configuration>()

    //TODO: definere lovlige datatyper -> tolkes_som. enum i koden

    post {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                val innkommendeFeltdefinisjonerDto = call.receive<FeltdefinisjonerDto>()

                feltdefinisjonTjeneste.oppdater(innkommendeFeltdefinisjonerDto)

                call.respond("OK")
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }
}

