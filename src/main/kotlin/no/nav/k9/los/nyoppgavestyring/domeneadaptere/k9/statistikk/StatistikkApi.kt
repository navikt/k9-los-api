package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.StatistikkApi() {
    val requestContextService by inject<RequestContextService>()
    val oppgavestatistikkTjeneste by inject<OppgavestatistikkTjeneste>()
    val config by inject<Configuration>()

    put {
        if (config.nyOppgavestyringRestAktivert()) {
            requestContextService.withRequestContext(call) {
                oppgavestatistikkTjeneste.kjør(kjørUmiddelbart = true)
                call.respond(HttpStatusCode.NoContent)
            }
        } else {
            call.respond(HttpStatusCode.Locked)
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
        requestContextService.withRequestContext(call) {
            val oppgavetype = call.parameters["oppgavetype"]!!
            oppgavestatistikkTjeneste.slettStatistikkgrunnlag(oppgavetype)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}