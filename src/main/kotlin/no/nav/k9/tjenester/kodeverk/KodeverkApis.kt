package no.nav.k9.tjenester.kodeverk

import io.ktor.server.application.call
import io.ktor.server.locations.Location
import io.ktor.server.locations.get
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import org.koin.ktor.ext.inject

fun Route.KodeverkApis() {
    val kodeverkTjeneste by inject<HentKodeverkTjeneste>()

    @Location("/kodeverk")
    class hentGruppertKodeliste

    get { _: hentGruppertKodeliste ->
        kodeverkTjeneste.hentGruppertKodeliste().let { call.respond(it) }
    }
}
