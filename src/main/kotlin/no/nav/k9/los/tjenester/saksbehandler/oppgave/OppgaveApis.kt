package no.nav.k9.los.tjenester.saksbehandler.oppgave

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.pdl.IPdlService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
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

    post("/reserver") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harTilgangTilReserveringAvOppgaver()) {
                val oppgaveIdMedOverstyringDto = call.receive<OppgaveIdMedOverstyringDto>()
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
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/reserverte") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
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
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    // Fjernes når V1 skal vekk
    post("/fa-oppgave-fra-ko") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harTilgangTilReserveringAvOppgaver()) {
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
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/opphev") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
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
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/forleng") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val forlengReservasjonDto = call.receive<ForlengReservasjonDto>()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                    kotlin.coroutines.coroutineContext.idToken().getUsername()
                )!!

                try {
                    call.respond(oppgaveApisTjeneste.forlengReservasjon(forlengReservasjonDto, innloggetBruker))
                } catch (e: FinnerIkkeDataException) {
                    call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/flytt") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
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
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/reservasjon/endre") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val reservasjonEndringDto = call.receive<List<ReservasjonEndringDto>>()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                    kotlin.coroutines.coroutineContext.idToken().getUsername()
                )!!
                try {
                    call.respond(oppgaveApisTjeneste.endreReservasjoner(reservasjonEndringDto, innloggetBruker))
                } catch (e: FinnerIkkeDataException) {
                    call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    // Fjernes når V1 skal vekk
    get("/antall") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val uuid = call.request.queryParameters["id"]!!
                call.respond(oppgaveTjeneste.hentAntallOppgaver(UUID.fromString(uuid)))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    // Fjernes når V1 skal vekk
    //erstattet av OppgaveKoApis--/{id}/oppgaver::GET
    get {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val queryParameter = call.request.queryParameters["id"]
                call.respond(
                    oppgaveTjeneste.hentNesteOppgaverIKø(UUID.fromString(queryParameter))
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    //WIP: siste behandlede oppgaver av saksbehandler. Skal virke så lenge vi vedlikeholder data i OppgaveV1. Må erstattes før V1 og V2 kan slettes
    post("/legg-til-behandlet-sak") {
        requestContextService.withRequestContext(call) { //TODO klageoppgaver
            if (pepClient.harBasisTilgang()) {
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
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    //WIP: siste behandlede oppgaver av saksbehandler. Skal virke så lenge vi vedlikeholder data i OppgaveV1. Må erstattes før V1 og V2 kan slettes
    get("/behandlede") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(oppgaveTjeneste.hentSisteBehandledeOppgaver())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/flytt/sok") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val params = call.receive<BrukerIdentDto>()
                val sokSaksbehandlerMedIdent = oppgaveTjeneste.sokSaksbehandler(params.brukerIdent)
                call.respond(sokSaksbehandlerMedIdent)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/saksbehandlere") {
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
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
