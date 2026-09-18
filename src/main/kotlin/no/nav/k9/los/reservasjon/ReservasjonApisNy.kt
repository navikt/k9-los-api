package no.nav.k9.los.reservasjon

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("nav.OppgaveApis")

internal fun Route.ReservasjonApisNy() {
    val requestContextService by inject<RequestContextService>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by inject<IPepClient>()
    val reservasjonApisTjeneste by inject<ReservasjonApisTjeneste>()

    post("/reserver") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harTilgangTilReserveringAvOppgaver()) {
                val oppgaveIdMedOverstyringDto = call.receive<OppgaveIdMedOverstyringDto>()
                val navident = coroutineContext.idToken().getNavIdent()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    navident,
                    pepClient.harTilgangTilKode6()
                )
                    ?: throw IllegalStateException("Fant ikke saksbehandler $navident ved forsøk på å reservasjon av oppgave")

                try {
                    log.info("Forsøker å ta reservasjon direkte på ${oppgaveIdMedOverstyringDto.oppgaveNøkkel.oppgaveEksternId} for ${innloggetBruker.navident}")
                    val oppgave = reservasjonApisTjeneste.reserverOppgave(
                        område = coroutineContext.område(),
                        innloggetBruker = innloggetBruker,
                        oppgaveIdMedOverstyringDto = oppgaveIdMedOverstyringDto
                    )
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
                val innloggetBrukerNavIdent = coroutineContext.idToken().getNavIdent()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    innloggetBrukerNavIdent,
                    pepClient.harTilgangTilKode6()
                )

                if (innloggetBruker != null) {
                    val reservasjonV3Dtos = reservasjonApisTjeneste.hentReserverteOppgaverForSaksbehandler(
                        område = coroutineContext.område(),
                        saksbehandler = innloggetBruker
                    )
                    call.respond(reservasjonV3Dtos)
                } else {
                    log.info("Innlogger bruker med brukernavn $innloggetBrukerNavIdent finnes ikke i saksbehandlertabellen")
                    call.respond(
                        HttpStatusCode.InternalServerError,
                        "Innlogger bruker med brukernavn $innloggetBrukerNavIdent finnes ikke i saksbehandlertabellen"
                    )
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/opphev") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val params = call.receive<List<AnnullerReservasjonDto>>()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    coroutineContext.idToken().getNavIdent(),
                    pepClient.harTilgangTilKode6()
                )!!

                try {
                    log.info(
                        "Opphever reservasjoner ${
                            params.map { it.oppgaveNøkkel }.joinToString(", ")
                        } (Gjort av ${innloggetBruker.navident})"
                    )
                    reservasjonApisTjeneste.annullerReservasjoner(coroutineContext.område(), params, innloggetBruker)
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
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    coroutineContext.idToken().getNavIdent(),
                    pepClient.harTilgangTilKode6()
                )!!

                try {
                    call.respond(reservasjonApisTjeneste.forlengReservasjon(
                        område = coroutineContext.område(),
                        forlengReservasjonDto = forlengReservasjonDto,
                        innloggetBruker = innloggetBruker
                    ))
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
                val params = call.receive<FlyttReservasjonDto>()

                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    coroutineContext.idToken().getNavIdent(),
                    pepClient.harTilgangTilKode6()
                )!!

                try {
                    log.info("Flytter reservasjonen til ${params.brukerIdent} (Gjort av ${innloggetBruker.navident})")
                    call.respond(reservasjonApisTjeneste.overførReservasjon(
                        område = coroutineContext.område(),
                        params = params,
                        innloggetBruker = innloggetBruker
                    ))
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
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    coroutineContext.idToken().getNavIdent(),
                    pepClient.harTilgangTilKode6()
                )!!
                try {
                    call.respond(reservasjonApisTjeneste.endreReservasjoner(
                        område = coroutineContext.område(),
                        reservasjonEndringDto = reservasjonEndringDto,
                        innloggetBruker = innloggetBruker
                    ))
                } catch (e: FinnerIkkeDataException) {
                    call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/flytt/sok") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val params = call.receive<BrukerIdentDto>()
                val sokSaksbehandlerMedIdent = saksbehandlerRepository.sokSaksbehandler(
                    params.brukerIdent,
                    område = coroutineContext.område(),
                    skjermet = pepClient.harTilgangTilKode6()
                )
                call.respond(sokSaksbehandlerMedIdent)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/saksbehandlere") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val alleSaksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere(
                    område = coroutineContext.område(),
                    skjermet = pepClient.harTilgangTilKode6()
                )
                val saksbehandlerDtoListe =
                    alleSaksbehandlere.filter { saksbehandler -> !saksbehandler.navn.isNullOrBlank() && !saksbehandler.navident.isNullOrBlank() }
                        .map { saksbehandler ->
                            SaksbehandlerDto(saksbehandler.navident!!, saksbehandler.navn!!)
                        }
                call.respond(saksbehandlerDtoListe)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/aktiv-reservasjon") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgaveNøkkel = OppgaveNøkkelDto(
                    call.queryParameters["oppgaveEksternId"]!!,
                    call.queryParameters["oppgaveTypeEksternId"]!!,
                    coroutineContext.område()
                )
                val aktivReservasjon = reservasjonApisTjeneste.hentAktivReservasjon(
                    område = coroutineContext.område(),
                    idToken = coroutineContext.idToken(),
                    oppgaveNøkkel = oppgaveNøkkel
                )
                if (aktivReservasjon != null) {
                    call.respond(aktivReservasjon)
                } else {
                    call.respond(HttpStatusCode.NoContent)
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
