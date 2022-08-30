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
            val innkommendeOppgavetyperDto = call.receive<OppgavetyperDto>()
            transactionalManager.transaction { tx ->
                // lås feltdefinisjoner for område og hent opp
                val eksisterendeFeltdefinisjoner = feltdefinisjonRepository.hent(innkommendeOppgavetyperDto.område, tx)
                val innkommendeOppgavetyper = Oppgavetyper(innkommendeOppgavetyperDto, eksisterendeFeltdefinisjoner)

                val eksisterendeOppgavetyper = oppgavetypeRepository.hent(innkommendeOppgavetyperDto.område, innkommendeOppgavetyperDto.definisjonskilde, tx)
                val (sletteListe, leggtilListe, oppdaterListe) = eksisterendeOppgavetyper.finnForskjell(innkommendeOppgavetyper)
                oppgavetypeRepository.fjern(sletteListe, tx)
                oppgavetypeRepository.leggTil(leggtilListe, tx)
                // TODO: oppdaterListe - for hvert element: sjekk diff oppgavefelter og insert/delete på deltalister
            }

            call.respond("OK")
        }
    }
}