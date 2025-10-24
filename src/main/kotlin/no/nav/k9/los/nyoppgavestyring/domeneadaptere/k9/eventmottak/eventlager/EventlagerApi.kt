package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import com.fasterxml.jackson.module.kotlin.readValue
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.nyoppgavestyring.forvaltning.K9KlageEventIkkeSensitiv
import no.nav.k9.los.nyoppgavestyring.forvaltning.K9PunsjEventIkkeSensitiv
import no.nav.k9.los.nyoppgavestyring.forvaltning.K9SakEventIkkeSensitiv
import no.nav.k9.los.nyoppgavestyring.forvaltning.K9TilbakeEventIkkeSensitiv
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import org.koin.ktor.ext.inject
import java.util.*
import kotlin.concurrent.thread

internal fun Route.EventlagerApi() {
    val requestContextService by inject<RequestContextService>()
    val eventlagerKonverteringsjobb by inject<EventlagerKonverteringsjobb>()
    val eventlagerKonverteringsservice by inject<EventlagerKonverteringsservice>()
    val eventRepository by inject<EventRepository>()
    val oppgaveAdapter by inject<EventTilOppgaveAdapter>()
    val transactionalManager by inject<TransactionalManager>()
    val pepClient by inject<IPepClient>()

    get("/eventer/{system}/{eksternId}", {
        tags("Forvaltning")
        description = "Hent ut eventhistorikk for en oppgave, nytt eventlager"
        request {
            pathParameter<String>("system") {
                description = "Kildesystem som har levert eventene"
                example("k9sak") {
                    value = "K9SAK"
                    description = "Oppgaver fra k9sak"
                }
                example("k9klage") {
                    value = "K9KLAGE"
                    description = "Oppgaver fra k9klage"
                }
            }
            pathParameter<String>("eksternId") {
                description = "Oppgavens eksterne Id, definert av innleverende fagsystem"
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val fagsystem = Fagsystem.fraKode(call.parameters["system"]!!)
                val eksternId = call.parameters["eksternId"]!!

                val eventStrenger =
                    transactionalManager.transaction { tx ->
                    eventRepository.hentAlleEventer(fagsystem, eksternId, tx).map { it.eventJson }
                }

                val eventerIkkeSensitive = when (fagsystem) {
                    Fagsystem.K9SAK -> {
                        val eventliste = eventStrenger.map { LosObjectMapper.instance.readValue<K9SakEventDto>(it) }.toList()
                        eventliste.map { event -> K9SakEventIkkeSensitiv(event) }
                    }
                    Fagsystem.K9TILBAKE -> {
                        val eventliste = eventStrenger.map { LosObjectMapper.instance.readValue<K9TilbakeEventDto>(it) }.toList()
                        eventliste.map { event -> K9TilbakeEventIkkeSensitiv(event) }
                    }
                    Fagsystem.K9KLAGE -> {
                        val eventliste = eventStrenger.map { LosObjectMapper.instance.readValue<K9KlageEventDto>(it) }.toList()
                        eventliste.map { event -> K9KlageEventIkkeSensitiv(event) }
                    }
                    Fagsystem.PUNSJ -> {
                        val eventliste = eventStrenger.map { LosObjectMapper.instance.readValue<PunsjEventDto>(it) }.toList()
                        eventliste.map { event -> K9PunsjEventIkkeSensitiv(event) }
                    }
                }
                call.respond(LosObjectMapper.instance.writeValueAsString(eventerIkkeSensitive))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

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