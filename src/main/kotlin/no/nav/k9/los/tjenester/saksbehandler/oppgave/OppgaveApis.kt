package no.nav.k9.los.tjenester.saksbehandler.oppgave

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.locations.post
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.pdl.IPdlService
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.nyoppgavestyring.reservasjon.ManglerTilgangException
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.*

private val log: Logger = LoggerFactory.getLogger("nav.OppgaveApis")

//TODO siste 10 saker for saksbhandler -- nye ppgaver? -- klagesaker funker ikke her p.t.
//TODO generell sikring kode6 - se etter feil
//TODO fjern reservasjonsid fra objekter til frontend
//TODO Auditlogging

internal fun Route.OppgaveApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveTjeneste by inject<OppgaveTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val oppgaveRepository by inject<OppgaveRepository>()
    val pepClient by inject<IPepClient>()
    val oppgaveApisTjeneste by inject<OppgaveApisTjeneste>()
    val pdlService by inject<IPdlService>()

    @Location("/reserver")
    class reserverOppgave
    post { _: reserverOppgave ->
        val oppgaveIdMedOverstyringDto = call.receive<OppgaveIdMedOverstyringDto>()
        requestContextService.withRequestContext(call) {
            if (!pepClient.harTilgangTilReservingAvOppgaver()) {
                call.respond(HttpStatusCode.Forbidden)
            } else {
                val brukernavn = kotlin.coroutines.coroutineContext.idToken().getUsername()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(brukernavn)
                    ?: throw IllegalStateException("Fant ikke saksbehandler $brukernavn ved forsøk på å reservasjon av oppgave")

                try {
                    log.info("Forsøker å ta reservasjon direkte på ${oppgaveIdMedOverstyringDto.oppgaveNøkkel.oppgaveEksternId} for ${innloggetBruker.brukerIdent}")
                    val oppgave = oppgaveApisTjeneste.reserverOppgave(innloggetBruker, oppgaveIdMedOverstyringDto)
                    call.respond(oppgave)
                } catch (e: ManglerTilgangException) {
                    call.respond(HttpStatusCode.Forbidden, e.message!!)
                }
            }
        }
    }

    @Location("/reserverte")
    class getReserverteOppgaver
    get { _: getReserverteOppgaver ->
        requestContextService.withRequestContext(call) {
            val innloggetBrukernavn = kotlin.coroutines.coroutineContext.idToken().getUsername()
            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                innloggetBrukernavn
            )

            if (innloggetBruker != null) {
                val reservasjonV3Dtos = oppgaveApisTjeneste.hentReserverteOppgaverForSaksbehandler(innloggetBruker)
                call.respond(reservasjonV3Dtos)
            } else {
                log.info("Innlogger bruker med brukernavn $innloggetBrukernavn finnes ikke i saksbehandlertabellen")
                call.respond(
                    HttpStatusCode.InternalServerError,
                    "Innlogger bruker med brukernavn $innloggetBrukernavn finnes ikke i saksbehandlertabellen"
                )
            }
        }
    }

    // Fjernes når V1 skal vekk
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
                brukerident = saksbehandler.brukerIdent!!
            )

            if (oppgaveFraKø != null) {
                log.info("RESERVASJONDEBUG: Lagt til ${saksbehandler.brukerIdent} oppgave=${oppgaveFraKø.eksternId}, beslutter=${oppgaveFraKø.tilBeslutter}, kø=${params.oppgaveKøId} (neste oppgave)")
                call.respond(oppgaveFraKø)
            } else {
                call.respond(HttpStatusCode.NotFound, "Fant ingen oppgave i valgt kø")
            }
        }
    }

    // TODO: Rename til annuller
    @Location("/opphev")
    class opphevReservasjoner
    post { _: opphevReservasjoner ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<List<AnnullerReservasjon>>()
            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!

            try {
                log.info(
                    "Opphever reservasjoner ${
                        params.map { it.oppgaveNøkkel }.joinToString(", ")
                    } (Gjort av ${innloggetBruker.brukerIdent})"
                )
                oppgaveApisTjeneste.annullerReservasjoner(params, innloggetBruker)
                call.respond(HttpStatusCode.OK) //TODO: Hva er evt meningsfullt å returnere her?
            } catch (e: FinnerIkkeDataException) {
                call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitte reservasjonsnøkler")
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
                log.info("Flytter reservasjonen ${params.oppgaveNøkkel.oppgaveEksternId} til ${params.brukerIdent} (Gjort av ${innloggetBruker.brukerIdent})")
                call.respond(oppgaveApisTjeneste.overførReservasjon(params, innloggetBruker))
            } catch (e: FinnerIkkeDataException) {
                call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
            }
        }
    }

    @Location("/flytt-til-forrige-saksbehandler") //TODO Antatt ikke i bruk
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
            val reservasjonEndringDto = call.receive<List<ReservasjonEndringDto>>()
            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!
            try {
                call.respond(oppgaveApisTjeneste.endreReservasjoner(reservasjonEndringDto, innloggetBruker))
            } catch (e: FinnerIkkeDataException) {
                call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
            }
        }
    }

    // Fjernes når V1 skal vekk
    @Deprecated("Gjelder bare for gamle køer. For nye køer, bruk OppgaveKoApis./{id}/antall-oppgaver")
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

    // Fjernes når V1 skal vekk
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
        requestContextService.withRequestContext(call) { //TODO klageoppgaver
            val oppgavenøkkel = call.receive<OppgaveNøkkelDto>()
            val eksternUuid = UUID.fromString(oppgavenøkkel.oppgaveEksternId)
            val oppgave = oppgaveRepository.hent(eksternUuid)
            val person = pdlService.person(oppgave.aktorId).person

            if (person == null) {
                log.warn("Fant ikke personen som er på oppgaven med eksternId $eksternUuid")
                call.respond(HttpStatusCode.InternalServerError, "Fant ikke personen på oppgaven")
            } else {
                val behandletOppgave = BehandletOppgave(oppgave, person)
                call.respond(
                    oppgaveTjeneste.leggTilBehandletOppgave(
                        coroutineContext.idToken().getUsername(),
                        behandletOppgave
                    )
                )
            }
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
            call.respond(sokSaksbehandlerMedIdent)
        }
    }

    @Location("/saksbehandlere")
    class getAlleSaksbehandlere
    get { _: getAlleSaksbehandlere ->
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val alleSaksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere()
                val saksbehandlerDtoListe =
                    alleSaksbehandlere.filter { saksbehandler -> !saksbehandler.navn.isNullOrBlank() && !saksbehandler.brukerIdent.isNullOrBlank() }
                        .map { saksbehandler ->
                            SaksbehandlerDto(saksbehandler.brukerIdent!!, saksbehandler.navn!!)
                        }
                call.respond(saksbehandlerDtoListe)
            } else {
                call.respond(emptyList<SaksbehandlerDto>())
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
    post { _: oppgaverPåSammeBruker ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveId>()
            call.respond(oppgaveTjeneste.aktiveOppgaverPåSammeBruker(UUID.fromString(params.oppgaveId)))
        }
    }
}
