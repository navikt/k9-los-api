package no.nav.k9.los.nyoppgavestyring.nyeogferdigstilte

import io.github.smiley4.ktorswaggerui.dsl.routing.get
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
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