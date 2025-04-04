package no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.feilhandtering.IllegalDeleteException
import no.nav.k9.los.nyoppgavestyring.feilhandtering.MissingDefaultException
import org.koin.ktor.ext.inject

// Må legge til tilgangskontroll dersom disse endepunktene aktiveres
internal fun Route.OppgavetypeApi() {
    val requestContextService by inject<RequestContextService>()
    val oppgavetypeTjeneste by inject<OppgavetypeTjeneste>()
    val config by inject<Configuration>()

    post {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                val innkommendeOppgavetyperDto = call.receive<OppgavetyperDto>()

                try {
                    oppgavetypeTjeneste.oppdater(innkommendeOppgavetyperDto)
                    call.respond("OK")
                } catch (e: IllegalDeleteException) {
                    call.respond(
                        HttpStatusCode.BadRequest, e.message!!
                    )
                } catch (e: MissingDefaultException) {
                    call.respond(HttpStatusCode.BadRequest, e.message!!)
                }
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }
}