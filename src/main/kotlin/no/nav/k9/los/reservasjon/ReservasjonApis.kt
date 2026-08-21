package no.nav.k9.los.reservasjon

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("nav.OppgaveApis")

//TODO generell sikring kode6 - se etter feil
//TODO fjern reservasjonsid fra objekter til frontend

internal fun Route.ReservasjonApis() {
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by inject<IPepClient>()
    val reservasjonApisTjeneste by inject<ReservasjonApisTjeneste>()

    post("/reserver") {
        medBrukerkontekst { kontekst ->
            val skjermet = pepClient.harTilgangTilKode6(kontekst)
            if (pepClient.harTilgangTilReserveringAvOppgaver(kontekst)) {
                val oppgaveIdMedOverstyringDto = call.receive<OppgaveIdMedOverstyringDto>()
                val navident = kontekst.bruker.navIdent
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(navident, skjermet)
                    ?: throw IllegalStateException("Fant ikke saksbehandler $navident ved forsøk på å reservasjon av oppgave")

                try {
                    log.info("Forsøker å ta reservasjon direkte på ${oppgaveIdMedOverstyringDto.oppgaveNøkkel.oppgaveEksternId} for ${innloggetBruker.navident}")
                    val oppgave = reservasjonApisTjeneste.reserverOppgave(innloggetBruker, oppgaveIdMedOverstyringDto, skjermet)
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
        medBrukerkontekst { kontekst ->
            val skjermet = pepClient.harTilgangTilKode6(kontekst)
            if (pepClient.harBasisTilgang(kontekst)) {
                val innloggetBrukerNavIdent = kontekst.bruker.navIdent
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(innloggetBrukerNavIdent, skjermet)

                if (innloggetBruker != null) {
                    val reservasjonV3Dtos = reservasjonApisTjeneste.hentReserverteOppgaverForSaksbehandler(innloggetBruker, kontekst)
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
        medBrukerkontekst { kontekst ->
            val skjermet = pepClient.harTilgangTilKode6(kontekst)
            if (pepClient.harBasisTilgang(kontekst)) {
                val params = call.receive<List<AnnullerReservasjonDto>>()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(kontekst.bruker.navIdent, skjermet)!!

                try {
                    log.info(
                        "Opphever reservasjoner ${
                            params.map { it.oppgaveNøkkel }.joinToString(", ")
                        } (Gjort av ${innloggetBruker.navident})"
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
        medBrukerkontekst { kontekst ->
            val skjermet = pepClient.harTilgangTilKode6(kontekst)
            if (pepClient.harBasisTilgang(kontekst)) {
                val forlengReservasjonDto = call.receive<ForlengReservasjonDto>()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    kontekst.bruker.navIdent, skjermet
                )!!

                try {
                    call.respond(reservasjonApisTjeneste.forlengReservasjon(forlengReservasjonDto, innloggetBruker, kontekst))
                } catch (e: FinnerIkkeDataException) {
                    call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/flytt") {
        medBrukerkontekst { kontekst ->
            val skjermet = pepClient.harTilgangTilKode6(kontekst)
            if (pepClient.harBasisTilgang(kontekst)) {
                val params = call.receive<FlyttReservasjonDto>()

                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    kontekst.bruker.navIdent, skjermet
                )!!

                try {
                    log.info("Flytter reservasjonen til ${params.brukerIdent} (Gjort av ${innloggetBruker.navident})")
                    call.respond(reservasjonApisTjeneste.overførReservasjon(params, innloggetBruker, skjermet, kontekst))
                } catch (e: FinnerIkkeDataException) {
                    call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/reservasjon/endre") {
        medBrukerkontekst { kontekst ->
            val skjermet = pepClient.harTilgangTilKode6(kontekst)
            if (pepClient.harBasisTilgang(kontekst)) {
                val reservasjonEndringDto = call.receive<List<ReservasjonEndringDto>>()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    kontekst.bruker.navIdent, skjermet
                )!!
                try {
                    call.respond(reservasjonApisTjeneste.endreReservasjoner(reservasjonEndringDto, innloggetBruker, skjermet, kontekst))
                } catch (e: FinnerIkkeDataException) {
                    call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/flytt/sok") {
        medBrukerkontekst { kontekst ->
            val skjermet = pepClient.harTilgangTilKode6(kontekst)
            if (pepClient.harBasisTilgang(kontekst)) {
                val params = call.receive<BrukerIdentDto>()
                val sokSaksbehandlerMedIdent = saksbehandlerRepository.sokSaksbehandler(params.brukerIdent, kontekst.område, skjermet)
                call.respond(sokSaksbehandlerMedIdent)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/saksbehandlere") {
        medBrukerkontekst { kontekst ->
            val skjermet = pepClient.harTilgangTilKode6(kontekst)
            if (pepClient.harBasisTilgang(kontekst)) {
                val alleSaksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere(kontekst.område, skjermet)
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
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val oppgaveNøkkel = OppgaveNøkkelDto(
                    call.queryParameters["oppgaveEksternId"]!!,
                    call.queryParameters["oppgaveTypeEksternId"]!!,
                    call.queryParameters["områdeEksternId"]!!
                )
                val aktivReservasjon = reservasjonApisTjeneste.hentAktivReservasjon(oppgaveNøkkel, kontekst)
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

    // TODO: Fjernes herfra med overgang til ny API-struktur. Erstattet i ReservasjonAdminApis
    get("/alle-reservasjoner") {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                call.respond(reservasjonApisTjeneste.hentAlleAktiveReservasjoner(kontekst))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
