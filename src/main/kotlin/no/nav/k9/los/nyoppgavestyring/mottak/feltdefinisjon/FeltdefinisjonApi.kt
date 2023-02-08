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
                } catch (e: PSQLException) {
                    if (e.sqlState.equals("23503")) {
                        call.respond(
                            HttpStatusCode.BadRequest,
                            "Constraint Violation. Forsøker å fjerne en feltdefinisjon som er i bruk i en oppgavedefinisjon"
                        )
                    }
                }
                call.respond("OK")
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }
}

