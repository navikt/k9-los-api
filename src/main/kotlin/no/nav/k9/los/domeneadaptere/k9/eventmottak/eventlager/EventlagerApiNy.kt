package no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager

import com.fasterxml.jackson.module.kotlin.readValue
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.put
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import no.nav.k9.los.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventDto
import no.nav.k9.los.domeneadaptere.k9.eventmottak.sak.K9SakEventDto
import no.nav.k9.los.domeneadaptere.k9.eventmottak.tilbakekrav.K9TilbakeEventDto
import no.nav.k9.los.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.forvaltning.K9KlageEventIkkeSensitiv
import no.nav.k9.los.forvaltning.K9PunsjEventIkkeSensitiv
import no.nav.k9.los.forvaltning.K9SakEventIkkeSensitiv
import no.nav.k9.los.forvaltning.K9TilbakeEventIkkeSensitiv
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekstUtenOmråde
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.kodeverk.Fagsystem
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.koin.ktor.ext.inject
import java.util.*
import kotlin.concurrent.thread

internal fun Route.EventlagerApiNy() {
    val eventRepository by inject<EventRepository>()
    val oppgaveAdapter by inject<EventTilOppgaveAdapter>()

    get("/eventer/{fagsystem}/{eksternId}", {
        description = "Hent ut eventhistorikk for en oppgave, nytt eventlager"
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
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
        medBrukerkontekstUtenOmråde { kontekst ->
            if (kontekst.kanLeggeUtDriftsmelding()) {
                val fagsystem = Fagsystem.fraKode(call.parameters["fagsystem"]!!)
                val eksternId = call.parameters["eksternId"]!!

                val eventStrenger = try {
                    eventRepository.hentAlleEventer(fagsystem, eksternId).map { it.eventJson }
                } catch (e: NullPointerException) {
                    call.respond(HttpStatusCode.NotFound)
                    return@medBrukerkontekstUtenOmråde
                }

                val eventerIkkeSensitive = when (fagsystem) {
                    Fagsystem.K9SAK -> {
                        val eventliste = eventStrenger.map { LosObjectMapper.prettyInstance.readValue<K9SakEventDto>(it) }.toList()
                        eventliste.map { event -> K9SakEventIkkeSensitiv(event) }
                    }
                    Fagsystem.K9TILBAKE -> {
                        val eventliste = eventStrenger.map { LosObjectMapper.prettyInstance.readValue<K9TilbakeEventDto>(it) }.toList()
                        eventliste.map { event -> K9TilbakeEventIkkeSensitiv(event) }
                    }
                    Fagsystem.K9KLAGE -> {
                        val eventliste = eventStrenger.map { LosObjectMapper.prettyInstance.readValue<K9KlageEventDto>(it) }.toList()
                        eventliste.map { event -> K9KlageEventIkkeSensitiv(event) }
                    }
                    Fagsystem.PUNSJ -> {
                        val eventliste = eventStrenger.map { LosObjectMapper.prettyInstance.readValue<K9PunsjEventDto>(it) }.toList()
                        eventliste.map { event -> K9PunsjEventIkkeSensitiv(event) }
                    }
                }
                call.respond(LosObjectMapper.prettyInstance.writeValueAsString(eventerIkkeSensitive))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    put("/spillAvDirtyEventer", {
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
    }) {
        medBrukerkontekst {
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
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
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
        val fagsystem = Fagsystem.fraKode(call.parameters["fagsystem"]!!)
        eventRepository.bestillHistorikkvask(fagsystem)

        call.respond(HttpStatusCode.NoContent)
    }

    put("bestillHistorikkvaskForEnkeltoppgave", {
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
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
        medBrukerkontekst {
            val fagsystem = Fagsystem.fraKode(call.parameters["fagsystem"]!!)
            val eksternId = call.parameters["eksternId"]!!
            eventRepository.bestillHistorikkvask(fagsystem, eksternId)

            call.respond(HttpStatusCode.NoContent)
        }
    }

}
