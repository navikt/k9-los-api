package no.nav.k9.los.reservasjon

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
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

    post("/reserver", {
        operationId = "reserverOppgave"
        summary = "Reserver oppgave"
        request {
            body<OppgaveIdMedOverstyringDto> { description = "Oppgaven som skal reserveres, med eventuell overstyring" }
        }
        response {
            HttpStatusCode.OK to { body<OppgaveStatusDto>() }
            HttpStatusCode.Forbidden to {
                body<String>()
                description = "Brukeren mangler tilgang til å reservere oppgaven"
            }
        }
    }) {
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

    get("/reserverte", {
        operationId = "hentReserverteOppgaver"
        summary = "Hent reserverte oppgaver"
        response {
            HttpStatusCode.OK to { body<List<ReservasjonV3Dto>>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
            HttpStatusCode.InternalServerError to {
                body<String>()
                description = "Innlogget bruker finnes ikke i saksbehandlertabellen"
            }
        }
    }) {
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

    post("/opphev", {
        operationId = "opphevReservasjoner"
        summary = "Opphev reservasjoner"
        request {
            body<List<AnnullerReservasjonDto>> { description = "Reservasjonene som skal oppheves" }
        }
        response {
            HttpStatusCode.OK to { description = "Reservasjonene er opphevet" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
            HttpStatusCode.NotFound to {
                body<String>()
                description = "Ingen aktiv reservasjon ble funnet"
            }
        }
    }) {
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

    post("/forleng", {
        operationId = "forlengReservasjon"
        summary = "Forleng reservasjon"
        request {
            body<ForlengReservasjonDto> { description = "Reservasjonen og ny sluttdato" }
        }
        response {
            HttpStatusCode.OK to { body<ReservasjonV3Dto>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
            HttpStatusCode.NotFound to {
                body<String>()
                description = "Ingen aktiv reservasjon ble funnet"
            }
        }
    }) {
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

    post("/flytt", {
        operationId = "flyttReservasjon"
        summary = "Flytt reservasjon"
        request {
            body<FlyttReservasjonDto> { description = "Reservasjonen og saksbehandleren den skal flyttes til" }
        }
        response {
            HttpStatusCode.OK to { body<ReservasjonV3Dto>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
            HttpStatusCode.NotFound to {
                body<String>()
                description = "Ingen aktiv reservasjon ble funnet"
            }
        }
    }) {
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

    post("/reservasjon/endre", {
        operationId = "endreReservasjoner"
        summary = "Endre reservasjoner"
        request {
            body<List<ReservasjonEndringDto>> { description = "Endringene som skal utføres på reservasjonene" }
        }
        response {
            HttpStatusCode.OK to { description = "Reservasjonene er endret" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
            HttpStatusCode.NotFound to {
                body<String>()
                description = "Ingen aktiv reservasjon ble funnet"
            }
        }
    }) {
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

    post("/flytt/sok", {
        operationId = "sokSaksbehandlerForReservasjon"
        summary = "Søk etter saksbehandler"
        request {
            body<BrukerIdentDto> { description = "Nav-ident, navn eller e-post det skal søkes etter" }
        }
        response {
            HttpStatusCode.OK to { body<no.nav.k9.los.saksbehandleradmin.Saksbehandler>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
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

    get("/saksbehandlere", {
        operationId = "hentSaksbehandlereForReservasjon"
        summary = "Hent saksbehandlere"
        response {
            HttpStatusCode.OK to { body<List<SaksbehandlerDto>>() }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler basistilgang" }
        }
    }) {
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

    get("/aktiv-reservasjon", {
        operationId = "hentAktivReservasjon"
        summary = "Hent aktiv reservasjon"
        request {
            queryParameter<String>("oppgaveEksternId") {
                description = "Oppgavens eksterne id"
                required = true
            }
            queryParameter<String>("oppgaveTypeEksternId") {
                description = "Oppgavetypens eksterne id"
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { body<ReservasjonV3Dto>() }
            HttpStatusCode.NoContent to { description = "Oppgaven har ingen aktiv reservasjon" }
            HttpStatusCode.Forbidden to { description = "Brukeren mangler tilgang til oppgaven" }
        }
    }) {
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
