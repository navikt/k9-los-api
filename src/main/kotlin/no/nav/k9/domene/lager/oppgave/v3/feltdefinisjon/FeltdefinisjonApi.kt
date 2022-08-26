package no.nav.k9.domene.lager.oppgave.v3.feltdefinisjon

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.FeltdefinisjonApi() {
    val feltdefinisjonRepository by inject<FeltdefinisjonRepository>()
    val requestContextService by inject<RequestContextService>()
    val transactionalManager by inject<TransactionalManager>()

    post {
        requestContextService.withRequestContext(call) {
            val innkommendeFeltdefinisjoner = call.receive<Feltdefinisjoner>()

            transactionalManager.transaction { tx ->
                val persisterteDatatyper = feltdefinisjonRepository.hent(innkommendeFeltdefinisjoner.område, tx)
                val (sletteListe, leggTilListe) = persisterteDatatyper.finnForskjeller(innkommendeFeltdefinisjoner)
                feltdefinisjonRepository.fjern(sletteListe, tx)
                feltdefinisjonRepository.leggTil(leggTilListe, innkommendeFeltdefinisjoner.område, tx)
            }
            call.respond("OK")
        }
    }
}