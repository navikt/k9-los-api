package no.nav.k9.domene.lager.oppgave.v3.datatype

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.Route
import io.ktor.routing.post
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.DatatypeApi() {
    val datatypeRepository by inject<DatatypeRepository>()
    val requestContextService by inject<RequestContextService>()
    val transactionalManager by inject<TransactionalManager>()

    post {
        requestContextService.withRequestContext(call) {
            val innkommendeDatatyper = call.receive<Datatyper>()

            transactionalManager.transaction { tx ->
                val persisterteDatatyper = datatypeRepository.hent(innkommendeDatatyper.område, tx)
                val (sletteListe, leggTilListe) = persisterteDatatyper.finnForskjeller(innkommendeDatatyper)
                datatypeRepository.fjern(sletteListe, tx)
                datatypeRepository.leggTil(leggTilListe, innkommendeDatatyper.område, tx)
            }
            call.respond("OK")
        }
    }
}