package no.nav.k9.los.reservasjon

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.feilhandtering.FinnerIkkeDataException
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
    val reservasjonApisTjeneste by inject<ReservasjonApisTjeneste>()

    post("/reserver") {
        medBrukerkontekst { bruker ->
            val skjermet = bruker.harTilgangTilKode6
            if (bruker.harTilgangTilReserveringAvOppgaver) {
                val oppgaveIdMedOverstyringDto = call.receive<OppgaveIdMedOverstyringDto>()
                val navident = bruker.navIdent
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
        medBrukerkontekst { bruker ->
            val skjermet = bruker.harTilgangTilKode6
            if (bruker.harBasisTilgang) {
                val innloggetBrukerNavIdent = bruker.navIdent
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(innloggetBrukerNavIdent, skjermet)

                if (innloggetBruker != null) {
                    val reservasjonV3Dtos = reservasjonApisTjeneste.hentReserverteOppgaverForSaksbehandler(innloggetBruker, bruker)
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
        medBrukerkontekst { bruker ->
            val skjermet = bruker.harTilgangTilKode6
            if (bruker.harBasisTilgang) {
                val params = call.receive<List<AnnullerReservasjonDto>>()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(bruker.navIdent, skjermet)!!

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
        medBrukerkontekst { bruker ->
            val skjermet = bruker.harTilgangTilKode6
            if (bruker.harBasisTilgang) {
                val forlengReservasjonDto = call.receive<ForlengReservasjonDto>()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    bruker.navIdent, skjermet
                )!!

                try {
                    call.respond(reservasjonApisTjeneste.forlengReservasjon(forlengReservasjonDto, innloggetBruker, bruker))
                } catch (e: FinnerIkkeDataException) {
                    call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/flytt") {
        medBrukerkontekst { bruker ->
            val skjermet = bruker.harTilgangTilKode6
            if (bruker.harBasisTilgang) {
                val params = call.receive<FlyttReservasjonDto>()

                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    bruker.navIdent, skjermet
                )!!

                try {
                    log.info("Flytter reservasjonen til ${params.brukerIdent} (Gjort av ${innloggetBruker.navident})")
                    call.respond(reservasjonApisTjeneste.overførReservasjon(params, innloggetBruker, skjermet, bruker))
                } catch (e: FinnerIkkeDataException) {
                    call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/reservasjon/endre") {
        medBrukerkontekst { bruker ->
            val skjermet = bruker.harTilgangTilKode6
            if (bruker.harBasisTilgang) {
                val reservasjonEndringDto = call.receive<List<ReservasjonEndringDto>>()
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    bruker.navIdent, skjermet
                )!!
                try {
                    call.respond(reservasjonApisTjeneste.endreReservasjoner(reservasjonEndringDto, innloggetBruker, skjermet, bruker))
                } catch (e: FinnerIkkeDataException) {
                    call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/flytt/sok") {
        medBrukerkontekst { bruker ->
            val skjermet = bruker.harTilgangTilKode6
            if (bruker.harBasisTilgang) {
                val params = call.receive<BrukerIdentDto>()
                val sokSaksbehandlerMedIdent = saksbehandlerRepository.sokSaksbehandler(params.brukerIdent, bruker.område, skjermet)
                call.respond(sokSaksbehandlerMedIdent)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/saksbehandlere") {
        medBrukerkontekst { bruker ->
            val skjermet = bruker.harTilgangTilKode6
            if (bruker.harBasisTilgang) {
                val alleSaksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere(bruker.område, skjermet)
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
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang) {
                val oppgaveNøkkel = OppgaveNøkkelDto(
                    call.queryParameters["oppgaveEksternId"]!!,
                    call.queryParameters["oppgaveTypeEksternId"]!!,
                    call.queryParameters["områdeEksternId"]!!
                )
                val aktivReservasjon = reservasjonApisTjeneste.hentAktivReservasjon(oppgaveNøkkel, bruker)
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
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer) {
                call.respond(reservasjonApisTjeneste.hentAlleAktiveReservasjoner(bruker))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
