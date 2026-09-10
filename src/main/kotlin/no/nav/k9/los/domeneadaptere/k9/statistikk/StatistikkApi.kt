package no.nav.k9.los.domeneadaptere.k9.statistikk

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.forvaltning.medDriftstilgang
import no.nav.k9.los.forvaltning.krevK9Drift
import org.koin.ktor.ext.inject
import kotlin.concurrent.thread

internal fun Route.StatistikkApi() {
    val oppgavestatistikkTjeneste by inject<OppgavestatistikkTjeneste>()

    put {
        medDriftstilgang { bruker ->
            bruker.krevK9Drift()
            thread(
                start = true,
                isDaemon = true,
                name = "Oppgavestatistikksender"
            ) {
                oppgavestatistikkTjeneste.spillAvUsendtStatistikk()
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }


    get("resendStatistikkFraStart/{oppgavetype}", {
        description = "Nullstill statistikksending for en oppgavetype, slik at alle oppgaver av den typen blir resendt til DVH"
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
        medDriftstilgang { bruker ->
            bruker.krevK9Drift()
            val oppgavetype = call.parameters["oppgavetype"]!!
            if (oppgavetype !in setOf("k9sak", "k9klage")) {
                call.respond(HttpStatusCode.BadRequest)
                return@medDriftstilgang
            }
            oppgavestatistikkTjeneste.slettStatistikkgrunnlag(oppgavetype)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
