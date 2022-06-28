package no.nav.k9.tjenester.kokriterier

import io.ktor.application.call
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.routing.Route
import org.koin.ktor.ext.inject

fun Route.KøkriterieApis() {
    val køkritierierTjeneste by inject<HentKøkritierierTjeneste>()

    @Location("/kokriterier")
    class hentKøkriterier
    get { _: hentKøkriterier ->
        køkritierierTjeneste.hentKøkriterier().let { call.respond(it) }
    }
}