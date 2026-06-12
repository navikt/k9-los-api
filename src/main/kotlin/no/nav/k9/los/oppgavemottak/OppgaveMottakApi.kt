package no.nav.k9.los.oppgavemottak

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import org.koin.ktor.ext.inject

// Må legge til tilgangskontroll dersom disse endepunktene aktiveres
internal fun Route.OppgaveV3Api() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveMottakTjeneste by inject<OppgaveMottakTjeneste>()
    val transactionalManager by inject<TransactionalManager>()
    val config by inject<Configuration>()

    put {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                val oppgaveDto = call.receive<OppgaveDto>()

                transactionalManager.transaction { tx ->
                    oppgaveMottakTjeneste.sjekkDuplikatOgProsesser(NyOppgaveversjon(oppgaveDto), tx)
                }

                call.respond("OK")
            }
        } else {
            call.respond(HttpStatusCode.Locked)
        }
    }

    get("/{område}/{oppgavetype}/{eksternId}/{eksternVersjon}") {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                call.respond(
                    transactionalManager.transaction { tx ->
                        oppgaveMottakTjeneste.hentOppgaveversjon(
                            område = call.parameters["område"]!!,
                            oppgavetype = call.parameters["oppgavetype"]!!,
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