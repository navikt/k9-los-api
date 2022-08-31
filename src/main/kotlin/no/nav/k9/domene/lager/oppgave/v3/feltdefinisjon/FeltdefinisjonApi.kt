package no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.domene.lager.oppgave.v3.omraade.OmrådeRepository
import no.nav.k9.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.FeltdefinisjonApi() {
    val feltdefinisjonRepository by inject<FeltdefinisjonRepository>()
    val områdeRepository by inject<OmrådeRepository>()
    val requestContextService by inject<RequestContextService>()
    val transactionalManager by inject<TransactionalManager>()

    post {
        requestContextService.withRequestContext(call) {
            val innkommendeFeltdefinisjonerDto = call.receive<FeltdefinisjonerDto>()

            transactionalManager.transaction { tx ->
                val område = områdeRepository.hentOmråde(innkommendeFeltdefinisjonerDto.område, tx)
                val eksisterendeFeltdefinisjoner = feltdefinisjonRepository.hent(område, tx)
                val innkommendeFeltdefinisjoner = Feltdefinisjoner(innkommendeFeltdefinisjonerDto, område)

                val (sletteListe, leggTilListe) = eksisterendeFeltdefinisjoner.finnForskjeller(innkommendeFeltdefinisjoner)
                feltdefinisjonRepository.fjern(sletteListe, tx)
                feltdefinisjonRepository.leggTil(leggTilListe, innkommendeFeltdefinisjoner.område, tx)
            }
            call.respond("OK")
        }
    }
}