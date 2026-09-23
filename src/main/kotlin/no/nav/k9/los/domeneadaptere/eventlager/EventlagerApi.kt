package no.nav.k9.los.domeneadaptere.eventlager

import com.fasterxml.jackson.module.kotlin.readValue
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domeneadaptere.eventmottak.k9.klage.K9KlageEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.k9.punsj.K9PunsjEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.k9.sak.K9SakEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.k9.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.domeneadaptere.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.forvaltning.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.utils.IkkeImplementertException
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import org.koin.ktor.ext.inject
import java.util.*
import kotlin.concurrent.thread

internal fun Route.EventlagerApi() {
    val requestContextService by inject<RequestContextService>()
    val eventRepository by inject<EventRepository>()
    val oppgaveAdapter by inject<EventTilOppgaveAdapter>()
    val pepClient by inject<IPepClient>()

    get("/eventer/{fagsystem}/{eksternId}", {
        tags("Forvaltning")
        description = "Hent ut eventhistorikk for en oppgave, nytt eventlager"
        request {
            pathParameter<Fagsystem>("fagsystem") {
                description = "Fagsystemet man vil ha eventkonvertering for"
                required = true
                example("K9SAK") {
                    value = Fagsystem.K9SAK
                    description = "K9 Sak"
                }
            }
            pathParameter<String>("eksternId") {
                description = "Oppgavens eksterne Id, definert av innleverende fagsystem"
            }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.kanLeggeUtDriftsmelding()) {
                val fagsystem = Fagsystem.fraParameter(call.parameters["fagsystem"]!!)
                val eksternId = call.parameters["eksternId"]!!

                val eventStrenger = try {
                    eventRepository.hentAlleEventer(fagsystem, eksternId).map { it.eventJson }
                } catch (_: NullPointerException) {
                    call.respond(HttpStatusCode.NotFound)
                    return@withRequestContext
                }

                val eventerIkkeSensitive = when (fagsystem) {
                    Fagsystem.K9SAK -> {
                        eventStrenger.map { LosObjectMapper.prettyInstance.readValue<K9SakEventDto>(it) }.toList()
                    }
                    Fagsystem.K9TILBAKE -> {
                        eventStrenger.map { LosObjectMapper.prettyInstance.readValue<K9TilbakeEventDto>(it) }.toList()
                    }
                    Fagsystem.K9KLAGE -> {
                        eventStrenger.map { LosObjectMapper.prettyInstance.readValue<K9KlageEventDto>(it) }.toList()
                    }
                    Fagsystem.PUNSJ -> {
                        eventStrenger.map { LosObjectMapper.prettyInstance.readValue<K9PunsjEventDto>(it) }.toList()
                    }
                    Fagsystem.UNGSAK -> {
                        eventStrenger.map { LosObjectMapper.prettyInstance.readValue<UngSakEventDto>(it) }.toList()
                    }
                    Fagsystem.UNGTILBAKE -> throw IkkeImplementertException("Fagsystem $fagsystem is not implemented yet")
                }
                val responsePayload = ForvaltningSensitiveObjectMapper.prettyInstance.writeValueAsString(eventerIkkeSensitive)
                call.respond(responsePayload)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
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

    put("/{fagsystem}/bestillHistorikkvask", {
        tags("Forvaltning")
        request {
            pathParameter<Fagsystem>("fagsystem") {
                description = "Fagsystemet man vil ha eventkonvertering for"
                required = true
                example("K9SAK") {
                    value = Fagsystem.K9SAK
                    description = "K9 Sak"
                }
            }
        }
    }) {
        val fagsystem = Fagsystem.fraParameter(call.parameters["fagsystem"]!!)
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
            val fagsystem = Fagsystem.fraParameter(call.parameters["fagsystem"]!!)
            val eksternId = call.parameters["eksternId"]!!
            eventRepository.bestillHistorikkvask(fagsystem, eksternId)

            call.respond(HttpStatusCode.NoContent)
        }
    }

}


