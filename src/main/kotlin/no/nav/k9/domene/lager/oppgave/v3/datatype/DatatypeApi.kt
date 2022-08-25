package no.nav.k9.domene.lager.oppgave.v3.datatype

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.k9.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.DatatypeApi() {
    val datatypeRepository by inject<DatatypeRepository>()
    val requestContextService by inject<RequestContextService>()

    post {
        requestContextService.withRequestContext(call) {
            val epost = call.receive<Datatyper>()
            // hent alle datatyper for innkommende område
            // sjekk diff
            // sett inn/fjern det som trengs
            // TODO oppdatering av datatyper venter vi med :)
            //call.respond(datatypeRepository)
        }
    }
}