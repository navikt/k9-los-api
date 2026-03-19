package no.nav.k9.los.nyoppgavestyring.reservasjon

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.nyoppgavestyring.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveNøkkelDto
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("nav.OppgaveApis")

//TODO generell sikring kode6 - se etter feil
//TODO fjern reservasjonsid fra objekter til frontend

internal fun Route.ReservasjonApis() {
    val requestContextService by inject<RequestContextService>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by inject<IPepClient>()
    val reservasjonApisTjeneste by inject<ReservasjonApisTjeneste>()

    post("/reserver") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harTilgangTilReserveringAvOppgaver()) {
                val oppgaveIdMedOverstyringDto = call.receive<OppgaveIdMedOverstyringDto>()
                val navident = kotlin.coroutines.coroutineContext.idToken().getNavIdent()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(navident)
                    ?: throw IllegalStateException("Fant ikke saksbehandler $navident ved forsøk på å reservasjon av oppgave")

                try {
                    log.info("Forsøker å ta reservasjon direkte på ${oppgaveIdMedOverstyringDto.oppgaveNøkkel.oppgaveEksternId} for ${innloggetBruker.brukerIdent}")
                    val oppgave = reservasjonApisTjeneste.reserverOppgave(innloggetBruker, oppgaveIdMedOverstyringDto)
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
                val innloggetBrukerNavIdent = kotlin.coroutines.coroutineContext.idToken().getNavIdent()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(innloggetBrukerNavIdent)

                if (innloggetBruker != null) {
                    val reservasjonV3Dtos = reservasjonApisTjeneste.hentReserverteOppgaverForSaksbehandler(innloggetBruker)
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
                val params = call.receive<List<AnnullerReservasjon>>()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(kotlin.coroutines.coroutineContext.idToken().getNavIdent())!!

                try {
                    log.info(
                        "Opphever reservasjoner ${
                            params.map { it.oppgaveNøkkel }.joinToString(", ")
                        } (Gjort av ${innloggetBruker.brukerIdent})"
                    )
                    reservasjonApisTjeneste.annullerReservasjoner(params, innloggetBruker)
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
                    kotlin.coroutines.coroutineContext.idToken().getNavIdent()
                )!!

                try {
                    call.respond(reservasjonApisTjeneste.forlengReservasjon(forlengReservasjonDto, innloggetBruker))
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

                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    kotlin.coroutines.coroutineContext.idToken().getNavIdent()
                )!!

                try {
                    log.info("Flytter reservasjonen ${params.oppgaveNøkkel.oppgaveEksternId} til ${params.brukerIdent} (Gjort av ${innloggetBruker.brukerIdent})")
                    call.respond(reservasjonApisTjeneste.overførReservasjon(params, innloggetBruker))
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
                    kotlin.coroutines.coroutineContext.idToken().getNavIdent()
                )!!
                try {
                    call.respond(reservasjonApisTjeneste.endreReservasjoner(reservasjonEndringDto, innloggetBruker))
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
                val sokSaksbehandlerMedIdent = saksbehandlerRepository.sokSaksbehandler(params.brukerIdent)
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

    get("/aktiv-reservasjon") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgaveNøkkel = OppgaveNøkkelDto(
                    call.queryParameters["oppgaveEksternId"]!!,
                    call.queryParameters["oppgaveTypeEksternId"]!!,
                    call.queryParameters["områdeEksternId"]!!
                )
                val aktivReservasjon = reservasjonApisTjeneste.hentAktivReservasjon(oppgaveNøkkel)
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

    // TODO: Dette er det nye stedet for endepunktet. Duplisert i AvdelingslederApis.
    //  Fjernes derfra når frontend er over på nytt endepunkt. Slett da denne kommentaren.
    get("/alle-reservasjoner") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(reservasjonApisTjeneste.hentAlleAktiveReservasjoner())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
