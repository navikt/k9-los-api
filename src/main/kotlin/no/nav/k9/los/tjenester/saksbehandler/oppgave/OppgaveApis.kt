package no.nav.k9.los.tjenester.saksbehandler.oppgave

import io.ktor.application.call
import io.ktor.http.HttpStatusCode
import io.ktor.locations.Location
import io.ktor.locations.get
import io.ktor.locations.post
import io.ktor.request.receive
import io.ktor.response.respond
import io.ktor.routing.Route
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.rest.idToken
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("nav.OppgaveApis")

internal fun Route.OppgaveApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveTjeneste by inject<OppgaveTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()

    class hentOppgaver

    get { _: hentOppgaver ->
        val queryParameter = call.request.queryParameters["id"]
        requestContextService.withRequestContext(call) {
            call.respond(
                oppgaveTjeneste.hentNesteOppgaverIKø(UUID.fromString(queryParameter))
            )
        }
    }

    @Location("/behandlede")
    class getBehandledeOppgaver

    get { _: getBehandledeOppgaver ->
        requestContextService.withRequestContext(call) {
            call.respond(oppgaveTjeneste.hentSisteBehandledeOppgaver())
        }
    }

    @Location("/reserverte")
    class getReserverteOppgaver

    get { _: getReserverteOppgaver ->
        requestContextService.withRequestContext(call) {
            call.respond(oppgaveTjeneste.hentSisteReserverteOppgaver())
        }
    }

    @Location("/antall")
    class hentAntallOppgaverForOppgavekø

    get { _: hentAntallOppgaverForOppgavekø ->
        requestContextService.withRequestContext(call) {
            var uuid = call.request.queryParameters["id"]
            if (uuid.isNullOrBlank()) {
                uuid = UUID.randomUUID().toString()
            }
            call.respond(oppgaveTjeneste.hentAntallOppgaver(UUID.fromString(uuid)!!))
        }
    }

    @Location("/reserver")
    class reserverOppgave

    post { _: reserverOppgave ->
        val oppgaveIdMedOverstyring = call.receive<OppgaveIdMedOverstyring>()
        requestContextService.withRequestContext(call) {
            call.respond(
                oppgaveTjeneste.reserverOppgave(
                    saksbehandlerRepository.finnSaksbehandlerMedEpost(kotlin.coroutines.coroutineContext.idToken().getUsername())!!.brukerIdent!!,
                    oppgaveIdMedOverstyring.overstyrIdent,
                    UUID.fromString(oppgaveIdMedOverstyring.oppgaveId),
                    oppgaveIdMedOverstyring.overstyrSjekk,
                    oppgaveIdMedOverstyring.overstyrBegrunnelse
                )
            )
        }
    }

    @Location("/fa-oppgave-fra-ko")
    class fåOppgaveFraKø
    post { _: fåOppgaveFraKø ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveKøIdDto>()

            val ident = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!.brukerIdent!!

            val oppgaveFraKø = oppgaveTjeneste.fåOppgaveFraKø(params.oppgaveKøId, ident)

            if (oppgaveFraKø != null) {
                log.info("RESERVASJONDEBUG: Lagt til $ident oppgave=${oppgaveFraKø.eksternId}, beslutter=${oppgaveFraKø.tilBeslutter}, kø=${params.oppgaveKøId} (neste oppgave)")
                call.respond(oppgaveFraKø)
            } else {
                call.respond(HttpStatusCode.NotFound, "Fant ingen oppgave i valgt kø")
            }
        }
    }

    @Location("/opphev")
    class opphevReservasjon
    post { _: opphevReservasjon ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OpphevReservasjonId>()
            call.respond(oppgaveTjeneste.frigiReservasjon(UUID.fromString(params.oppgaveId), params.begrunnelse))
        }
    }

    @Location("/oppgaver-på-samme-bruker")
    class oppgaverPåSammeBruker
    post { _: opphevReservasjon ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveId>()
            call.respond(oppgaveTjeneste.aktiveOppgaverPåSammeBruker(UUID.fromString(params.oppgaveId)))
        }
    }

    @Location("/legg-til-behandlet-sak")
    class leggTilBehandletSak

    post { _: leggTilBehandletSak ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<BehandletOppgave>()
            call.respond(
                oppgaveTjeneste.leggTilBehandletOppgave(
                    coroutineContext.idToken().getUsername(),
                    params
                )
            )
        }
    }

    @Location("/forleng")
    class forlengReservasjon
    post { _: forlengReservasjon ->
        requestContextService.withRequestContext(call) {
            val oppgaveId = call.receive<OppgaveId>()
            call.respond(oppgaveTjeneste.forlengReservasjonPåOppgave(UUID.fromString(oppgaveId.oppgaveId)))
        }
    }

    @Location("/flytt")
    class flyttReservasjon

    post { _: flyttReservasjon ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<FlyttReservasjonId>()
            call.respond(
                oppgaveTjeneste.flyttReservasjon(
                    UUID.fromString(params.oppgaveId),
                    params.brukerIdent,
                    params.begrunnelse
                )
            )
        }
    }

    @Location("/reservasjon/endre")
    class endreReservasjon

    post { _: endreReservasjon ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<ReservasjonEndringDto>()
            call.respond(
                oppgaveTjeneste.endreReservasjonPåOppgave(params)
            )
        }
    }

    @Location("/flytt-til-forrige-saksbehandler")
    class flyttReservasjonTilForrigeSaksbehandler

    post { _: flyttReservasjonTilForrigeSaksbehandler ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveId>()
            call.respond(
                oppgaveTjeneste.flyttReservasjonTilForrigeSakbehandler(UUID.fromString(params.oppgaveId))
            )
        }
    }

    @Location("/hent-historiske-reservasjoner-på-oppgave")
    class hentHistoriskeReservasjonerPåOppgave

    post { _: hentHistoriskeReservasjonerPåOppgave ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveId>()
            call.respond(
                oppgaveTjeneste.hentReservasjonsHistorikk(UUID.fromString(params.oppgaveId))
            )
        }
    }

    @Location("/flytt/sok")
    class søkSaksbehandler

    post { _: søkSaksbehandler ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<BrukerIdentDto>()
            val sokSaksbehandlerMedIdent = oppgaveTjeneste.sokSaksbehandler(params.brukerIdent)
            if (sokSaksbehandlerMedIdent == null) {
                call.respond("")
            } else {
                call.respond(sokSaksbehandlerMedIdent)
            }
        }
    }

    @Location("/oppgaver-for-fagsaker")
    class oppgaverForFagsaker

    get { _: oppgaverForFagsaker ->
        requestContextService.withRequestContext(call) {
            val saker = call.request.queryParameters["saksnummerListe"]
            val saksnummerliste = saker?.split(",") ?: emptyList()
            val oppgaver = oppgaveTjeneste.hentOppgaverFraListe(saksnummerliste)
            val result = mutableListOf<OppgaveDto>()
            if (oppgaver.isNotEmpty()) {
                val oppgaverBySaksnummer = oppgaver.groupBy { it.saksnummer }
                for (entry in oppgaverBySaksnummer.entries) {
                    val x = entry.value.firstOrNull { oppgaveDto -> oppgaveDto.erTilSaksbehandling }
                    if (x != null) {
                        result.add(x)
                    } else {
                        result.add(entry.value.first())
                    }
                }
                call.respond(result)
            } else {
                call.respond(oppgaver)
            }
        }
    }
}