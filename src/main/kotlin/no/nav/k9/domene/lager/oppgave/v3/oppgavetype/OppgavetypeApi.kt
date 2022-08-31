package no.nav.k9.domene.lager.oppgave.v3.oppgavetype

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon.FeltdefinisjonRepository
import no.nav.k9.domene.lager.oppgave.v3.omraade.OmrådeRepository
import no.nav.k9.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.OppgavetypeApi() {
    val oppgavetypeRepository by inject<OppgavetypeRepository>()
    val områdeRepository by inject<OmrådeRepository>()
    val feltdefinisjonRepository by inject<FeltdefinisjonRepository>()
    val requestContextService by inject<RequestContextService>()
    val transactionalManager by inject<TransactionalManager>()

    post {
        requestContextService.withRequestContext(call) {
            val innkommendeOppgavetyperDto = call.receive<OppgavetyperDto>()
            transactionalManager.transaction { tx ->
                val område = områdeRepository.hentOmråde(innkommendeOppgavetyperDto.område, tx)
                // lås feltdefinisjoner for område og hent opp
                val eksisterendeFeltdefinisjoner = feltdefinisjonRepository.hent(område, tx)
                val innkommendeOppgavetyper = Oppgavetyper(innkommendeOppgavetyperDto, eksisterendeFeltdefinisjoner)

                val eksisterendeOppgavetyper = oppgavetypeRepository.hent(område, tx)
                val (sletteListe, leggtilListe, oppdaterListe) = eksisterendeOppgavetyper.finnForskjell(innkommendeOppgavetyper)
                oppgavetypeRepository.fjern(sletteListe, tx)
                oppgavetypeRepository.leggTil(leggtilListe, tx)
                // TODO: oppdaterListe - for hvert element: sjekk diff oppgavefelter og insert/delete på deltalister
                if (oppdaterListe.oppgavetyper.size > 0) {
                    throw IllegalArgumentException("Endring av oppgavefeltliste til oppgavetype er ikke implementert ennå. Oppgavetypen må slettes og lages på nytt inntil videre")
                }
            }

            call.respond("OK")
        }
    }
}