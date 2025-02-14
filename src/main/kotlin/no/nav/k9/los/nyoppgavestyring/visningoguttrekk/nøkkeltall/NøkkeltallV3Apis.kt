package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall.DagensTallService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet.FerdigstiltePerEnhetGruppe
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet.FerdigstiltePerEnhetService
import org.koin.ktor.ext.inject

fun Route.NøkkeltallV3Apis() {
    val dagensTallService by inject<DagensTallService>()
    val perEnhetService by inject<FerdigstiltePerEnhetService>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    get("dagens-tall") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(dagensTallService.hentCachetVerdi())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("ferdigstilte-per-enhet") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val gruppe = call.parameters["gruppe"]?.let { FerdigstiltePerEnhetGruppe.valueOf(it) }
                    ?: FerdigstiltePerEnhetGruppe.ALLE
                val uker = call.parameters["uker"]?.toInt() ?: 2
                call.respond(perEnhetService.hentCachetVerdi(gruppe, uker))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}