package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import io.github.smiley4.ktoropenapi.put
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import org.koin.ktor.ext.inject
import java.util.UUID
import kotlin.concurrent.thread
import kotlin.getValue

internal fun Route.EventlagerApi() {
    val requestContextService by inject<RequestContextService>()
    val eventlagerKonverteringsjobb by inject<EventlagerKonverteringsjobb>()
    val eventlagerKonverteringsservice by inject<EventlagerKonverteringsservice>()
    val eventRepository by inject <EventRepository>()
    val oppgaveAdapter by inject <EventTilOppgaveAdapter>()
    val transactionalManager by inject<TransactionalManager>()

    put("/startEventlagerKonvertering", {
        tags("Forvaltning")
    }) {
        eventlagerKonverteringsjobb.kjørEventlagerKonvertering()
        call.respond(HttpStatusCode.NoContent)
    }

    put("/spillAvDirtyEventer", {
        tags("Forvaltning")
    }) {
        requestContextService.withRequestContext(call) {
            thread(
                start = true,
                isDaemon = true,
                name = oppgaveAdapter.javaClass.simpleName,
            ) {
                oppgaveAdapter.spillAvBehandlingProsessEventer()
            }

            call.respond(HttpStatusCode.NoContent)
        }
    }

    put("konverterEnkeltoppgave", {
        tags("Forvaltning")
        request {
            queryParameter<Fagsystem>("fagsystem") {
                description = "Fagsystemet man vil ha eventkonvertering for"
                required = true
                example("oneOf") {
                    value = Fagsystem.K9SAK
                }
            }
            queryParameter<String>("eksternId") {
                description = "Ekstern ID for oppgaven"
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            val fagsystem = Fagsystem.fraKode(call.parameters["fagsystem"]!!)
            val eksternId = call.parameters["eksternId"]!!
            transactionalManager.transaction { tx ->
                eventlagerKonverteringsservice.konverterOppgave(eksternId, fagsystem, tx, false)
            }
            call.respond(HttpStatusCode.NoContent)
        }
    }

    put("bestillHistorikkvaskForFagsystem", {
        tags("Forvaltning")
        request {
            queryParameter<Fagsystem>("fagsystem") {
                description = "Fagsystemet man vil ha historikkvask for"
                required = true
                example("oneOf") {
                    value = Fagsystem.K9SAK
                }
            }
        }
    }) {
        val fagsystem = Fagsystem.fraKode(call.parameters["fagsystem"]!!)
        eventRepository.bestillHistorikkvask(fagsystem)

        call.respond(HttpStatusCode.NoContent)
    }

    put("bestillHistorikkvaskForEnkeltoppgave", {
        tags("Forvaltning")
        request {
            queryParameter<Fagsystem>("fagsystem") {
                description = "Fagsystemet for oppgaven man vil ha historikkvask for"
                required = true
                example("oneOf") {
                    value = Fagsystem.K9SAK
                }
            }
            queryParameter<String>("eksternId") {
                description = "Ekstern ID for oppgaven man vil ha historikkvask for"
                required = true
                example("eksternId") {
                    value = UUID.randomUUID()
                }
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            val fagsystem = Fagsystem.fraKode(call.parameters["fagsystem"]!!)
            val eksternId = call.parameters["eksternId"]!!
            eventRepository.bestillHistorikkvask(fagsystem, eksternId)

            call.respond(HttpStatusCode.NoContent)
        }
    }
}