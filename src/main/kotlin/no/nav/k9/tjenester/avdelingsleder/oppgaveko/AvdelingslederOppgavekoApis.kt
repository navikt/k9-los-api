package no.nav.k9.tjenester.avdelingsleder.oppgaveko

import io.ktor.application.*
import io.ktor.locations.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.util.*
import no.nav.k9.KoinProfile
import no.nav.k9.integrasjon.rest.RequestContextService
import no.nav.k9.tjenester.avdelingsleder.AvdelingslederTjeneste
import org.koin.ktor.ext.inject
import java.util.*

fun Route.AvdelingslederOppgavekøApis() {
    val avdelingslederTjeneste by inject<AvdelingslederTjeneste>()
    val requestContextService by inject<RequestContextService>()

    class hentAlleOppgaveKøer
    get { _: hentAlleOppgaveKøer ->
        requestContextService.withRequestContext(call) {
            call.respond(avdelingslederTjeneste.hentOppgaveKøer())
        }
    }

    class opprettOppgaveKø
    post { _: opprettOppgaveKø ->
        requestContextService.withRequestContext(call) {
            call.respond(avdelingslederTjeneste.opprettOppgaveKø())
        }
    }

    @Location("/navn")
    class endreOppgavekoNavn
    post { _: endreOppgavekoNavn ->
        requestContextService.withRequestContext(call) {
            val uuid = call.receive<OppgavekøNavnDto>()
            call.respond(avdelingslederTjeneste.endreOppgavekøNavn(uuid))
        }
    }

    @Location("/slett")
    class slettOppgaveKø
    post { _: slettOppgaveKø ->
        requestContextService.withRequestContext(call) {
            val uuid = call.receive<IdDto>()
            call.respond(avdelingslederTjeneste.slettOppgavekø(UUID.fromString(uuid.id)))
        }
    }

    @Location("/hent")
    class hentOppgaveKø
    get { _: hentOppgaveKø ->
        requestContextService.withRequestContext(call) {
            val uuid = call.request.queryParameters.get("id")
            call.respond(avdelingslederTjeneste.hentOppgaveKø(UUID.fromString(uuid)))
        }
    }

    @Location("/behandlingstype")
    class lagreBehandlingstype
    post { _: lagreBehandlingstype ->
        requestContextService.withRequestContext(call) {
            val behandling = call.receive<BehandlingsTypeDto>()
            call.respond(avdelingslederTjeneste.endreBehandlingsType(behandling))
        }
    }

    @Location("/skjermet")
    class lagreSkjermet
    post { _: lagreSkjermet ->
        requestContextService.withRequestContext(call) {
            val behandling = call.receive<SkjermetDto>()
            call.respond(avdelingslederTjeneste.endreSkjerming(behandling))
        }
    }

    @Location("/ytelsetype")
    class lagreYtelsestype
    post { _: lagreYtelsestype ->
        requestContextService.withRequestContext(call) {
            val ytelse = call.receive<YtelsesTypeDto>()
            call.respond(avdelingslederTjeneste.endreYtelsesType(ytelse))
        }
    }

    @Location("/andre-kriterier")
    class endreKriterier
    post { _: endreKriterier ->
        requestContextService.withRequestContext(call) {
            val kriterium = call.receive<AndreKriterierDto>()
            call.respond(avdelingslederTjeneste.endreKriterium(kriterium))
        }
    }

    @Location("/sortering")
    class lagreSortering
    post { _: lagreSortering ->
        requestContextService.withRequestContext(call) {
            val sortering = call.receive<KøSorteringDto>()
            call.respond(avdelingslederTjeneste.endreKøSortering(sortering))
        }
    }

    @Location("/sortering-tidsintervall-dato")
    class lagreSorteringType
    post { _: lagreSorteringType ->
        requestContextService.withRequestContext(call) {
            val sortering = call.receive<SorteringDatoDto>()
            call.respond(avdelingslederTjeneste.endreKøSorteringDato(sortering))
        }
    }

    @Location("/saksbehandler")
    class leggFjernSaksbehandlerOppgaveko
    post { _: leggFjernSaksbehandlerOppgaveko ->
        requestContextService.withRequestContext(call) {
            val saksbehandler = call.receive<SaksbehandlerOppgavekoDto>()
            call.respond(avdelingslederTjeneste.leggFjernSaksbehandlerOppgavekø(saksbehandler))
        }
    }
}
