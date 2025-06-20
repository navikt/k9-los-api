package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import org.koin.ktor.ext.inject

fun Route.NokkeltallApis() {
    val nokkeltallTjeneste by inject<NokkeltallTjeneste>()
    val requestContextService by inject<RequestContextService>()
    val pepClient by inject<IPepClient>()

    get("/nye-ferdigstilte-oppsummering") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(nokkeltallTjeneste.hentNyeFerdigstilteOppgaverOppsummering())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/dagens-tall") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(nokkeltallTjeneste.hentDagensTall())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/aksjonspunkter-per-enhet-historikk") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val historikk = nokkeltallTjeneste.hentFerdigstilteOppgaverHistorikk(
                    VelgbartHistorikkfelt.DATO,
                    VelgbartHistorikkfelt.ENHET,
                    VelgbartHistorikkfelt.YTELSETYPE,
                    VelgbartHistorikkfelt.FAGSYSTEM
                )
                call.respond(historikk)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}