package no.nav.k9.los.nøkkeltall.saksbehandler.nyeogferdigstilte

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import org.koin.ktor.ext.inject

fun Route.NyeOgFerdigstilteApi() {
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()
    val nyeOgFerdigstilteService by inject<NyeOgFerdigstilteService>()

    get {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(nyeOgFerdigstilteService.hentCachetVerdi(call.parameters["gruppe"]?.let { NyeOgFerdigstilteGruppe.valueOf(it) } ?: NyeOgFerdigstilteGruppe.ALLE))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}