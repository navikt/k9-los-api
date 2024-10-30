package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.koin.ktor.ext.inject


//TODO Er det greit at denne ikke finner klageoppgaver frem til ferdig overgang til V3?
fun Route.NokkeltallApis() {
    val nokkeltallTjeneste by inject<NokkeltallTjeneste>()
    val oppgaveTjeneste by inject<OppgaveTjeneste>()
    val requestContextService by inject<RequestContextService>()

    get("/behandlinger-under-arbeid") {
        requestContextService.withRequestContext(call) {
            call.respond(nokkeltallTjeneste.hentOppgaverUnderArbeid())
        }
    }

    get("/beholdning-historikk") {
        call.respond(oppgaveTjeneste.hentBeholdningAvOppgaverPerAntallDager())
    }

    get("/nye-ferdigstilte-oppsummering") {
        call.respond(nokkeltallTjeneste.hentNyeFerdigstilteOppgaverOppsummering())
    }

    get("/dagens-tall") {
        requestContextService.withRequestContext(call) {
            call.respond(nokkeltallTjeneste.hentDagensTall())
        }
    }

    get("/ferdigstilte-historikk") {
        call.respond(nokkeltallTjeneste.hentFerdigstilteSiste8Uker())
    }

    get("/aksjonspunkter-per-enhet-historikk") {
        val historikk = nokkeltallTjeneste.hentFerdigstilteOppgaverHistorikk(
            VelgbartHistorikkfelt.DATO,
            VelgbartHistorikkfelt.ENHET,
            VelgbartHistorikkfelt.YTELSETYPE,
            VelgbartHistorikkfelt.FAGSYSTEM
        )
        call.respond(historikk)
    }

    get("/nye-historikk") {
        call.respond(nokkeltallTjeneste.hentNyeSiste8Uker())
    }

    get("/alle-paa-vent_v2") {
        call.respond(nokkeltallTjeneste.hentOppgaverPåVentV2())
    }
}