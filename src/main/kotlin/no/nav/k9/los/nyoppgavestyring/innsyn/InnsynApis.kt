package no.nav.k9.los.nyoppgavestyring.innsyn

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepositoryTxWrapper
import no.nav.k9.los.utils.LosObjectMapper
import org.koin.ktor.ext.inject


fun Route.innsynApis() {

    val k9sakEventRepository by inject<BehandlingProsessEventK9Repository>()
    val oppgaveRepositoryTxWrapper by inject<OppgaveRepositoryTxWrapper>()
    val objectMapper = LosObjectMapper.instance

    get("/eventer") {
        val fagsystem = Fagsystem.fraKode(call.request.queryParameters["system"]!!)
        val behandlingUuid = call.request.queryParameters["behandlingUuid"]!!
        /*call.respond(when (fagsystem) {
            Fagsystem.K9SAK -> {
                val k9SakModell = k9sakEventRepository.hent(UUID.fromString(behandlingUuid))
                val eventerIkkeSensitive = k9SakModell.eventer.map { event -> K9SakEventIkkeSensitiv(event) }
                objectMapper.writeValueAsString(eventerIkkeSensitive)


                TODO()
            }

            Fagsystem.K9TILBAKE -> TODO()
            Fagsystem.FPTILBAKE -> TODO()
            Fagsystem.PUNSJ -> TODO()
            Fagsystem.OMSORGSPENGER -> HttpStatusCode.NotImplemented
        })
         */

        call.respond(HttpStatusCode.NotImplemented)
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
}