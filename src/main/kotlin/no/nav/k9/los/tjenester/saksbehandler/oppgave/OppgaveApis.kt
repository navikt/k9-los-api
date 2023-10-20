package no.nav.k9.los.tjenester.saksbehandler.oppgave

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.locations.post
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("nav.OppgaveApis")

//TODO limit 10 på neste oppgaver i kø
//TODO siste 10 saker for saksbhandler -- nye ppgaver?
//TODO kode6 filtrering på neste 10 oppgaver fra kø?
//TODO bare levere reservasjonID til frontend!! Verifiser
//TODO Auditlogging

internal fun Route.OppgaveApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveTjeneste by inject<OppgaveTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val pepClient by inject<IPepClient>()
    val oppgaveApisTjeneste by inject<OppgaveApisTjeneste>()

    @Location("/reserver")
    class reserverOppgave
    post { _: reserverOppgave ->
        val oppgaveIdMedOverstyring = call.receive<OppgaveIdMedOverstyring>()
        requestContextService.withRequestContext(call) {
            if (!pepClient.harTilgangTilReservingAvOppgaver()) {
                call.respond(HttpStatusCode.Forbidden)
            } else {
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                    kotlin.coroutines.coroutineContext.idToken().getUsername()
                )!!

                call.respond(oppgaveApisTjeneste.reserverOppgave(innloggetBruker, oppgaveIdMedOverstyring))
            }
        }
    }

    @Location("/reserverte")
    class getReserverteOppgaver
    get { _: getReserverteOppgaver ->
        requestContextService.withRequestContext(call) {
            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!

            val reservasjonV3Dtos = oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(innloggetBruker)
            call.respond(reservasjonV3Dtos)
        }
    }

    //TODO: Flytt til OppgaveKoApis
    @Location("/fa-oppgave-fra-ny-ko")
    class fåOppgaveFraNyKø
    post { _: fåOppgaveFraNyKø ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveKøIdDto>()

            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!

            val (reservertOppgave, reservasjonFraKø) = oppgaveKoTjeneste.taReservasjonFraKø(
                innloggetBrukerId = innloggetBruker.id!!,
                oppgaveKoId = params.oppgaveKøId.toLong()
            ) ?: Pair(null, null)

            if (reservasjonFraKø != null) {
                call.respond(ReservasjonV3FraKøDto(reservasjonFraKø, reservertOppgave!!, innloggetBruker))
            } else {
                call.respond(HttpStatusCode.NotFound, "Fant ingen oppgave i valgt kø")
            }
        }
    }

    @Deprecated("Gjelder bare for de gamle køene, frem til disse er sanert")
    @Location("/fa-oppgave-fra-ko")
    class fåOppgaveFraKø
    post { _: fåOppgaveFraKø ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveKøIdDto>()

            val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!

            //reservasjonV3 skjer i enden av oppgaveTjeneste.fåOppgaveFraKø()
            val oppgaveFraKø = oppgaveTjeneste.fåOppgaveFraKø(
                oppgaveKøId = params.oppgaveKøId,
                brukerident = saksbehandler.brukerIdent!!,
                saksbehandlerEpost = saksbehandler.epost!!
            )

            if (oppgaveFraKø != null) {
                log.info("RESERVASJONDEBUG: Lagt til ${saksbehandler.brukerIdent} oppgave=${oppgaveFraKø.eksternId}, beslutter=${oppgaveFraKø.tilBeslutter}, kø=${params.oppgaveKøId} (neste oppgave)")
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
            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!

            try {
                oppgaveApisTjeneste.annullerReservasjon(params, innloggetBruker)
                call.respond(HttpStatusCode.OK) //TODO: Hva er evt meningsfullt å returnere her?
            } catch (e: FinnerIkkeDataException) {
                call.respond(HttpStatusCode.NotFound,"Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
            }
        }
    }

    @Location("/forleng")  //TODO maks 1 uke inntil videre?
    class forlengReservasjon
    post { _: forlengReservasjon ->
        requestContextService.withRequestContext(call) {
            val forlengReservasjonDto = call.receive<ForlengReservasjonDto>()
            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!

            try {
                call.respond(oppgaveApisTjeneste.forlengReservasjon(forlengReservasjonDto, innloggetBruker))
            } catch (e: FinnerIkkeDataException) {
                call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
            }
        }
    }

    @Location("/flytt")
    class flyttReservasjon
    post { _: flyttReservasjon ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<FlyttReservasjonId>()

            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!
            try {
                call.respond(oppgaveApisTjeneste.overførReservasjon(params, innloggetBruker))
            } catch (e: FinnerIkkeDataException) {
                call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
            }
        }
    }

    @Location("/flytt-til-forrige-saksbehandler") //TODO
    class flyttReservasjonTilForrigeSaksbehandler
    post { _: flyttReservasjonTilForrigeSaksbehandler ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveId>()
            call.respond(
                HttpStatusCode.NotImplemented
                //oppgaveTjeneste.flyttReservasjonTilForrigeSakbehandler(UUID.fromString(params.oppgaveId))
            )
        }
    }

    @Location("/reservasjon/endre")
    class endreReservasjon
    post { _: endreReservasjon ->
        requestContextService.withRequestContext(call) {
            val reservasjonEndringDto = call.receive<ReservasjonEndringDto>()
            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!
            try {
                call.respond(oppgaveApisTjeneste.endreReservasjon(reservasjonEndringDto, innloggetBruker))
            } catch (e: FinnerIkkeDataException) {
                call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
            }
        }
    }

    @Location("/antall-oppgaver-i-ko")
    class hentAntallOppgaverForV3OppgaveKø
    get {_: hentAntallOppgaverForV3OppgaveKø ->
        requestContextService.withRequestContext(call) {
            var oppgaveKøId = call.request.queryParameters["oppgaveKoId"]!!
            call.respond(oppgaveApisTjeneste.hentAntallOppgaverIKø(oppgaveKøId))
        }
    }


    @Deprecated("Gjelder bare for gamle køer. For nye køer, bruk /antall-oppgaver-i-ko")
    @Location("/antall")
    class hentAntallOppgaverForOppgavekø
    get { _: hentAntallOppgaverForOppgavekø ->
        requestContextService.withRequestContext(call) {
            var uuid = call.request.queryParameters["id"]
            if (uuid.isNullOrBlank()) {
                uuid = UUID.randomUUID().toString() //TODO: wtf!?
            }
            call.respond(oppgaveTjeneste.hentAntallOppgaver(UUID.fromString(uuid)!!))
        }
    }

    @Deprecated("Gjelder bare for gamle køer. For nye køer, se OppgaveKoApis./{id}/oppgaver")
    class hentOppgaver
    //erstattet av OppgaveKoApis--/{id}/oppgaver::GET
    get { _: hentOppgaver ->
        val queryParameter = call.request.queryParameters["id"]
        requestContextService.withRequestContext(call) {
            call.respond(
                oppgaveTjeneste.hentNesteOppgaverIKø(UUID.fromString(queryParameter))

            )
        }
    }

    @Location("/legg-til-behandlet-sak")  //WIP: siste behandlede oppgaver av saksbehandler. Skal virke så lenge vi vedlikeholder data i OppgaveV1. Må erstattes før V1 og V2 kan slettes
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

    @Location("/behandlede") //WIP: siste behandlede oppgaver av saksbehandler. Skal virke så lenge vi vedlikeholder data i OppgaveV1. Må erstattes før V1 og V2 kan slettes
    class getBehandledeOppgaver
    get { _: getBehandledeOppgaver ->
        requestContextService.withRequestContext(call) {
            call.respond(oppgaveTjeneste.hentSisteBehandledeOppgaver())
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

    @Deprecated("Antatt ikke i bruk. Verifiser og fjern")
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

    @Deprecated("Antatt ikke i bruk. Verifiser og fjern")
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

    @Deprecated("Antatt ikke i bruk. Verifiser og fjern")
    @Location("/oppgaver-på-samme-bruker")
    class oppgaverPåSammeBruker
    post { _: opphevReservasjon ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveId>()
            call.respond(oppgaveTjeneste.aktiveOppgaverPåSammeBruker(UUID.fromString(params.oppgaveId)))
        }
    }
}
