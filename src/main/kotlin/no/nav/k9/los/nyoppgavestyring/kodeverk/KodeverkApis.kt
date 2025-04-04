package no.nav.k9.los.nyoppgavestyring.kodeverk

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import org.koin.ktor.ext.inject

fun Route.KodeverkApis() {
    val kodeverkTjeneste by inject<HentKodeverkTjeneste>()
    val requestContextService by inject<RequestContextService>()

    get {
        requestContextService.withRequestContext(call) {
            kodeverkTjeneste.hentGruppertKodeliste().let { call.respond(it) }
        }
    }
}
