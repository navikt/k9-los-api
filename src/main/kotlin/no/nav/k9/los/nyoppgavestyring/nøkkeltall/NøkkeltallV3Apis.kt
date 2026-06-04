package no.nav.k9.los.nyoppgavestyring.nøkkeltall

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.nøkkeltall.avdelingsleder.dagenstall.DagensTallService
import no.nav.k9.los.nyoppgavestyring.nøkkeltall.avdelingsleder.ferdigstilteperenhet.FerdigstiltePerEnhetGruppe
import no.nav.k9.los.nyoppgavestyring.nøkkeltall.avdelingsleder.ferdigstilteperenhet.FerdigstiltePerEnhetService
import no.nav.k9.los.nyoppgavestyring.nøkkeltall.avdelingsleder.statusfordeling.StatusFordelingService
import no.nav.k9.los.nyoppgavestyring.nøkkeltall.avdelingsleder.status.StatusService
import org.koin.ktor.ext.inject

fun Route.NøkkeltallV3Apis() {
    val statusFordelingService by inject<no.nav.k9.los.nyoppgavestyring.nøkkeltall.avdelingsleder.statusfordeling.StatusFordelingService>()
    val statusService by inject<no.nav.k9.los.nyoppgavestyring.nøkkeltall.avdelingsleder.status.StatusService>()
    val dagensTallService by inject<no.nav.k9.los.nyoppgavestyring.nøkkeltall.avdelingsleder.dagenstall.DagensTallService>()
    val perEnhetService by inject<no.nav.k9.los.nyoppgavestyring.nøkkeltall.avdelingsleder.ferdigstilteperenhet.FerdigstiltePerEnhetService>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    get("status") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(statusService.hentStatus(pepClient.harTilgangTilKode6()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("statusfordeling") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val kode6 = pepClient.harTilgangTilKode6()
                call.respond(statusFordelingService.hentVerdi(kode6))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

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