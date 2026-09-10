package no.nav.k9.los.domeneadaptere.k9.statistikk

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.PepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import org.koin.ktor.ext.inject
import kotlin.concurrent.thread

internal fun Route.StatistikkApi() {
    val requestContextService by inject<RequestContextService>()
    val oppgavestatistikkTjeneste by inject<OppgavestatistikkTjeneste>()
    val pepClient by inject<PepClient>()

    put {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                thread(
                    start = true,
                    isDaemon = true,
                    name = "Oppgavestatistikksender"
                ) {
                    oppgavestatistikkTjeneste.spillAvUsendtStatistikk()
                }

                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }


    get("resendStatistikkFraStart/{oppgavetype}", {
        tags("Forvaltning")
        description =
            "Nullstill statistikksending for en oppgavetype, slik at alle oppgaver av den typen blir resendt til DVH"
        request {
            pathParameter<String>("oppgavetype") {
                description = "Oppgavetypen man vil resende"
                example("k9sak") {
                    value = "k9sak"
                    description = "Oppgaver fra k9sak"
                }
                example("k9klage") {
                    value = "k9klage"
                    description = "Oppgaver fra k9klage"
                }
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val oppgavetype = call.parameters["oppgavetype"]!!
                oppgavestatistikkTjeneste.slettStatistikkgrunnlag(oppgavetype)
                call.respond(HttpStatusCode.NoContent)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}