package no.nav.k9.los.tjenester.kodeverk

import io.ktor.application.call
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.routing.Route
import org.koin.ktor.ext.inject

fun Route.KodeverkApis() {
    val kodeverkTjeneste by inject<HentKodeverkTjeneste>()
    @Location("/kodeverk")
    class hentGruppertKodeliste

    get { _: hentGruppertKodeliste ->
        kodeverkTjeneste.hentGruppertKodeliste().let { call.respond(it) }
    }
}
