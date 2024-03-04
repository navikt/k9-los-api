package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import no.nav.k9.los.Configuration
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.feilhandtering.IllegalDeleteException
import org.koin.ktor.ext.inject
import org.postgresql.util.PSQLException

internal fun Route.FeltdefinisjonApi() {
    val requestContextService by inject<RequestContextService>()
    val feltdefinisjonTjeneste by inject<FeltdefinisjonTjeneste>()
    val config by inject<Configuration>()

    //TODO: definere lovlige datatyper -> tolkes_som. enum i koden

    post {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                val innkommendeFeltdefinisjonerDto = call.receive<FeltdefinisjonerDto>()

                try {
                    feltdefinisjonTjeneste.oppdater(innkommendeFeltdefinisjonerDto)
                    call.respond("OK")
                } catch (e: IllegalDeleteException) {
                    call.respond(HttpStatusCode.BadRequest, e.message!!)
                }
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }

    post {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                val kodeverkDto = call.receive<KodeverkDto>()
                feltdefinisjonTjeneste.oppdater(kodeverkDto)
            }
        }
    }
}

