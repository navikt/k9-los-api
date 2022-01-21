package no.nav.k9.tjenester.avdelingsleder.nokkeltall

import io.ktor.application.call
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.response.respond
import io.ktor.routing.Route
import no.nav.k9.integrasjon.rest.RequestContextService
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.koin.ktor.ext.inject

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
    class hentFerdigstilteSiste8Uker

    get { _: hentFerdigstilteSiste8Uker ->
        call.respond(nokkeltallTjeneste.hentFerdigstilteSiste8Uker())
    }

    @Location("/ferdigstilte-enhet-historikk")
    class hentFerdigstilteEnhet

    get { _: hentFerdigstilteEnhet ->
        val historikk = nokkeltallTjeneste.hentFerdigstilteOppgavePrEnhetHistorikk()
            .map {
                FerdigstillelseHistorikkEnhetDto(
                    dato = it.key,
                    behandlendeEnhet = it.value.map { (enhet, antall) ->
                        FerdigstillelseHistorikkEnhetDto.AntallPrEnhet(enhet, antall)
                    })
        }
        call.respond(historikk)
    }


    @Location("/ferdigstilte-historikk-alle")
    class hentFerdigstilteEnhetAlle

    get { _: hentFerdigstilteEnhetAlle ->
        call.respond(nokkeltallTjeneste.hentFerdigstiltOppgavehistorikk().map { it.tilDto() })
    }

    @Location("/nye-historikk")
    class hentNyeSiste8Uker

    get { _: hentNyeSiste8Uker ->
        call.respond(nokkeltallTjeneste.hentNyeSiste8Uker())
    }

    @Location("/alle-paa-vent")
    class HentAllePåVent

    get { _: HentAllePåVent ->
        call.respond(nokkeltallTjeneste.hentOppgaverPåVent())
    }

}