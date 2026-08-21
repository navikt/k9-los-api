package no.nav.k9.los.nøkkeltall

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.nøkkeltall.avdelingsleder.dagenstall.DagensTallService
import no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet.FerdigstiltePerEnhetGruppe
import no.nav.k9.los.nøkkeltall.avdelingsleder.ferdigstilteperenhet.FerdigstiltePerEnhetService
import no.nav.k9.los.nøkkeltall.avdelingsleder.statusfordeling.StatusFordelingService
import no.nav.k9.los.nøkkeltall.avdelingsleder.status.StatusService
import org.koin.ktor.ext.inject

fun Route.NøkkeltallV3Apis() {
    val statusFordelingService by inject<StatusFordelingService>()
    val statusService by inject<StatusService>()
    val dagensTallService by inject<DagensTallService>()
    val perEnhetService by inject<FerdigstiltePerEnhetService>()

    get("status") {
        medBrukerkontekst { kontekst ->
            if (kontekst.erOppgavestyrer()) {
                call.respond(statusService.hentStatus(kontekst.harTilgangTilKode6()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("statusfordeling") {
        medBrukerkontekst { kontekst ->
            if (kontekst.erOppgavestyrer()) {
                val kode6 = kontekst.harTilgangTilKode6()
                call.respond(statusFordelingService.hentVerdi(kode6))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("dagens-tall") {
        medBrukerkontekst { kontekst ->
            if (kontekst.erOppgavestyrer()) {
                call.respond(dagensTallService.hentCachetVerdi())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("ferdigstilte-per-enhet") {
        medBrukerkontekst { kontekst ->
            if (kontekst.erOppgavestyrer()) {
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
