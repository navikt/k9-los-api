package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.OppgavetypeApi() {
    val oppgavetypeRepository by inject<OppgavetypeRepository>()
    val feltdefinisjonRepository by inject<FeltdefinisjonRepository>()
    val requestContextService by inject<RequestContextService>()
    val transactionalManager by inject<TransactionalManager>()

    post {
        requestContextService.withRequestContext(call) {
            val innkommendeOppgavetyperDTO = call.receive<OppgavetyperDTO>()
            transactionalManager.transaction { tx ->
                // lås feltdefinisjoner for område og hent opp
                val eksisterendeFeltdefinisjoner = feltdefinisjonRepository.hent(innkommendeOppgavetyperDTO.område, tx)
                val innkommendeOppgavetyper = Oppgavetyper(innkommendeOppgavetyperDTO, eksisterendeFeltdefinisjoner)
                // hent alle oppgavetyper for innkommende område
                val eksisterendeOppgavetyper = oppgavetypeRepository.hent(innkommendeOppgavetyperDTO.område, innkommendeOppgavetyperDTO.definisjonskilde, tx)
                // sjekk diff
                val (sletteListe, leggtilListe, oppdaterListe) = eksisterendeOppgavetyper.finnForskjell(innkommendeOppgavetyper)
                // 3 lister
                // sletteliste - for hvert element: slett først oppgavefelter, så oppgavetype
                oppgavetypeRepository.fjern(sletteListe, tx)
                // leggtilListe - for hvert element: insert først oppgavetype, så oppgavefelter
                oppgavetypeRepository.leggTil(leggtilListe, tx)
                // oppdaterListe - for hvert element: sjekk diff oppgavefelter og insert/delete på deltalister
            }

            call.respond("OK")
        }
    }
}