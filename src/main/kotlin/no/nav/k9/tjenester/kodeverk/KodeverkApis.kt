package no.nav.k9.tjenester.kodeverk

import io.ktor.application.call
import io.ktor.response.respond
import io.ktor.routing.Route
import io.ktor.routing.get
import org.koin.ktor.ext.inject

fun Route.KodeverkApis() {
    val kodeverkTjeneste by inject<HentKodeverkTjeneste>()

    get("/kodeverk") {
        kodeverkTjeneste.hentGruppertKodeliste2().let { call.respond(it) }
    }
}
