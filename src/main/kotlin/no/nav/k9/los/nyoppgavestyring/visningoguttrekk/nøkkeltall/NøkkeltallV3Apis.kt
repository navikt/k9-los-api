package no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.dagenstall.DagensTallService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.nøkkeltall.ferdigstilteperenhet.FerdigstiltPerEnhetService
import org.koin.ktor.ext.inject

fun Route.NøkkeltallV3Apis() {
    val dagensTallService by inject<DagensTallService>()
    val perEnhetService by inject<FerdigstiltPerEnhetService>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    route("dagens-tall") {
        post("oppdater") {
            requestContextService.withRequestContext(call) {
                if (pepClient.erOppgaveStyrer()) {
                    dagensTallService.oppdaterCache(this)
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }

        get {
            requestContextService.withRequestContext(call) {
                if (pepClient.erOppgaveStyrer()) {
                    call.respond(dagensTallService.hentCachetVerdi())
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }
    }

    route("per-enhet") {
        post("oppdater") {
            requestContextService.withRequestContext(call) {
                if (pepClient.erOppgaveStyrer()) {
                    perEnhetService.oppdaterCache(this)
                    call.respond(HttpStatusCode.OK)
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }

        get {
            requestContextService.withRequestContext(call) {
                if (pepClient.erOppgaveStyrer()) {
                    call.respond(perEnhetService.hentCachetVerdi())
                } else {
                    call.respond(HttpStatusCode.Forbidden)
                }
            }
        }
    }


}