package no.nav.k9.los.tjenester.avdelingsleder.nokkeltall

import io.ktor.server.application.*
import io.ktor.server.locations.*
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

    @Location("/behandlinger-under-arbeid")
    class getAlleOppgaver

    get { _: getAlleOppgaver ->
        requestContextService.withRequestContext(call) {
            call.respond(nokkeltallTjeneste.hentOppgaverUnderArbeid())
        }
    }

    @Location("/beholdning-historikk")
    class getAntallOppgaverPerDato

    get { _: getAntallOppgaverPerDato ->
        call.respond(oppgaveTjeneste.hentBeholdningAvOppgaverPerAntallDager())
    }

    @Location("/nye-ferdigstilte-oppsummering")
    class getNyeFerdigstilteOppgaver

    get { _: getNyeFerdigstilteOppgaver ->
        call.respond(nokkeltallTjeneste.hentNyeFerdigstilteOppgaverOppsummering())
    }

    @Location("/dagens-tall")
    class hentDagensTall

    get { _: hentDagensTall ->
        requestContextService.withRequestContext(call) {
            call.respond(nokkeltallTjeneste.hentDagensTall())
        }
    }

    @Location("/ferdigstilte-historikk")
    class HentFerdigstilteSiste8Uker

    get { _: HentFerdigstilteSiste8Uker ->
        call.respond(nokkeltallTjeneste.hentFerdigstilteSiste8Uker())
    }

    @Location("/aksjonspunkter-per-enhet-historikk")
    class HentFullførteOppgaverPrEnhetOgYtelse

    get { _: HentFullførteOppgaverPrEnhetOgYtelse ->
        val historikk = nokkeltallTjeneste.hentFerdigstilteOppgaverHistorikk(
            VelgbartHistorikkfelt.DATO,
            VelgbartHistorikkfelt.ENHET,
            VelgbartHistorikkfelt.YTELSETYPE,
            VelgbartHistorikkfelt.FAGSYSTEM
        )
        call.respond(historikk)
    }

    @Location("/nye-historikk")
    class hentNyeSiste8Uker

    get { _: hentNyeSiste8Uker ->
        call.respond(nokkeltallTjeneste.hentNyeSiste8Uker())
    }

    @Location("/alle-paa-vent_v2")
    class HentAllePåVentV2

    get { _: HentAllePåVentV2 ->
        call.respond(nokkeltallTjeneste.hentOppgaverPåVentV2())
    }

}