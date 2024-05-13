package no.nav.k9.los.nyoppgavestyring.forvaltning

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.domene.repository.BehandlingProsessEventKlageRepository
import no.nav.k9.los.domene.repository.BehandlingProsessEventTilbakeRepository
import no.nav.k9.los.domene.repository.PunsjEventK9Repository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosHistorikkvaskTjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import no.nav.k9.los.utils.LosObjectMapper
import org.koin.ktor.ext.inject
import java.util.*


fun Route.forvaltningApis() {

    val k9sakEventRepository by inject<BehandlingProsessEventK9Repository>()
    val k9tilbakeEventRepository by inject<BehandlingProsessEventTilbakeRepository>()
    val k9klageEventRepository by inject<BehandlingProsessEventKlageRepository>()
    val k9PunsjEventK9Repository by inject<PunsjEventK9Repository>()
    val oppgaveRepositoryTxWrapper by inject<OppgaveRepositoryTxWrapper>()
    val k9SakTilLosHistorikkvaskTjeneste by inject<K9SakTilLosHistorikkvaskTjeneste>()
    val objectMapper = LosObjectMapper.instance

    get("/eventer/{system}/{eksternId}") {
        val fagsystem = Fagsystem.fraKode(call.parameters["system"]!!)
        val eksternId = call.parameters["eksternId"]
        when (fagsystem) {
            Fagsystem.K9SAK -> {
                val k9SakModell = k9sakEventRepository.hent(UUID.fromString(eksternId))
                if (k9SakModell.eventer.isEmpty()) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    val eventerIkkeSensitive = k9SakModell.eventer.map { event -> K9SakEventIkkeSensitiv(event) }
                    call.respond(objectMapper.writeValueAsString(eventerIkkeSensitive))
                }
            }

            Fagsystem.K9TILBAKE -> {
                val k9TilbakeModell = k9tilbakeEventRepository.hent(UUID.fromString(eksternId))
                val eventerIkkeSensitive = k9TilbakeModell.eventer.map { event -> K9TilbakeEventIkkeSensitiv(event) }
                objectMapper.writeValueAsString(eventerIkkeSensitive)
            }

            Fagsystem.K9KLAGE -> {
                val k9KlageModell = k9klageEventRepository.hent(UUID.fromString(eksternId))
                val eventerIkkeSensitive = k9KlageModell.eventer.map { event -> K9KlageEventIkkeSensitiv(event) }
                objectMapper.writeValueAsString(eventerIkkeSensitive)
            }

            Fagsystem.PUNSJ -> {
                val k9PunsjModell = k9PunsjEventK9Repository.hent(UUID.fromString(eksternId))
                val eventerIkkeSensitive = k9PunsjModell.eventer.map { event -> K9PunsjEventIkkeSensitiv(event) }
                objectMapper.writeValueAsString(eventerIkkeSensitive)
            }

            Fagsystem.OMSORGSPENGER -> HttpStatusCode.NotImplemented
            Fagsystem.FPTILBAKE -> HttpStatusCode.NotImplemented
        }
    }

    get("/oppgaveV3/{omrade}/{oppgavetype}/{oppgaveEksternId}") {
        val område = call.parameters["omrade"]!!
        val oppgavetype = call.parameters["oppgavetype"]!!
        val oppgaveEksternId = call.parameters["oppgaveEksternId"]!!

        val oppgaveTidsserie =
            oppgaveRepositoryTxWrapper.hentOppgaveTidsserie(område, oppgavetype, oppgaveEksternId)
        if (oppgaveTidsserie.isEmpty()) {
            call.respond(HttpStatusCode.NotFound)
        } else {
            val tidsserieIkkeSensitiv = oppgaveTidsserie.map { oppgave -> OppgaveIkkeSensitiv(oppgave) }
            call.respond(objectMapper.writeValueAsString(tidsserieIkkeSensitiv))
        }
    }

    get("/oppgaveV3/{omrade}/{oppgavetype}/{oppgaveEksternId}/historikkvask") {
        val område = call.parameters["omrade"]!!
        val oppgavetype = call.parameters["oppgavetype"]!!
        val oppgaveEksternId = call.parameters["oppgaveEksternId"]!!

        when(område) {
            "K9" -> {
                when(oppgavetype) {
                    "k9sak" -> {
                        k9SakTilLosHistorikkvaskTjeneste.vaskOppgaveForBehandlingUUID(UUID.fromString(oppgaveEksternId), 0)
                        call.respond(HttpStatusCode.NoContent)
                    }
                    else -> call.respond(HttpStatusCode.NotImplemented, "Støtter ikke historikkvask på oppgavetype: $oppgavetype for område: $område")
                }
            }
            else -> call.respond(HttpStatusCode.NotImplemented, "Støtter ikke historikkvask på område: $område")
        }
    }
}