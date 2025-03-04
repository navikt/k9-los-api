package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.db.util.TransactionalManager
import no.nav.k9.los.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

// Må legge til tilgangskontroll dersom disse endepunktene aktiveres
internal fun Route.OppgaveV3Api() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveV3Tjeneste by inject<OppgaveV3Tjeneste>()
    val transactionalManager by inject<TransactionalManager>()
    val config by inject<Configuration>()

    put {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                val oppgaveDto = call.receive<OppgaveDto>()

                transactionalManager.transaction { tx ->
                    oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto, tx)
                }

                call.respond("OK")
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }

    get("/{område}/{eksternId}/{eksternVersjon}") {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                call.respond(
                    transactionalManager.transaction { tx ->
                        oppgaveV3Tjeneste.hentOppgaveversjon(
                            område = call.parameters["område"]!!,
                            eksternId = call.parameters["eksternId"]!!,
                            eksternVersjon = call.parameters["eksternVersjon"]!!,
                            tx = tx
                        )
                    }
                )
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }

    post ( "/{område}/{eksternId}/{eksternVersjon}" ) {
        call.respond(HttpStatusCode.NotImplemented)
        /*
        if (config.nyOppgavestyringRestAktivert()) {

            requestContextService.withRequestContext(call) {
                val oppgaveDto = call.receive<OppgaveDto>()
                transactionalManager.transaction { tx ->
                    oppgaveV3Tjeneste.oppdaterEkstisterendeOppgaveversjon(oppgaveDto, tx)
                }
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }

         */
    }
}