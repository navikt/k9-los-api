package no.nav.k9.tjenester.avdelingsleder

import io.ktor.application.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.k9.integrasjon.rest.RequestContextService
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveId
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.koin.ktor.ext.inject
import java.util.*

internal fun Route.AvdelingslederApis() {
    val oppgaveTjeneste by inject<OppgaveTjeneste>()
    val avdelingslederTjeneste by inject<AvdelingslederTjeneste>()
    val requestContextService by inject<RequestContextService>()

    @Location("/oppgaver/antall-totalt")
    class hentAntallOppgaverTotalt
    get { _: hentAntallOppgaverTotalt ->
        requestContextService.withRequestContext(call) {
            call.respond(oppgaveTjeneste.hentAntallOppgaverTotalt())
        }
    }

    @Location("/oppgaver/antall")
    class hentAntallOppgaver
    get { _: hentAntallOppgaver ->
        requestContextService.withRequestContext(call) {
            val uuid = call.parameters["id"]
            call.respond(oppgaveTjeneste.hentAntallOppgaver(oppgavekøId = UUID.fromString(uuid), taMedReserverte = true))
        }
    }

    @Location("/saksbehandlere")
    class hentSaksbehandlere
    get { _: hentSaksbehandlere ->
        requestContextService.withRequestContext(call) {
            call.respond(avdelingslederTjeneste.hentSaksbehandlere())
        }
    }

    @Location("/saksbehandlere/sok")
    class søkSaksbehandler
    post { _: søkSaksbehandler ->
        requestContextService.withRequestContext(call) {
            val epost = call.receive<EpostDto>()
            call.respond(avdelingslederTjeneste.søkSaksbehandler(epost))
        }
    }


    @Location("/saksbehandlere/slett")
    class slettSaksbehandler
    post { _: slettSaksbehandler ->
        requestContextService.withRequestContext(call) {
            val epost = call.receive<EpostDto>()
            call.respond(avdelingslederTjeneste.fjernSaksbehandler(epost.epost))
        }
    }

    @Location("/reservasjoner")
    class hentReservasjoner
    get { _: hentReservasjoner ->
        requestContextService.withRequestContext(call) {
            call.respond(avdelingslederTjeneste.hentAlleReservasjoner())
        }
    }

    @Location("/reservasjoner/opphev")
    class opphevReservasjon
    post { _: opphevReservasjon ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveId>()
            call.respond(avdelingslederTjeneste.opphevReservasjon(UUID.fromString(params.oppgaveId)))
        }
    }
}
