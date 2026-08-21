package no.nav.k9.los.reservasjon

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val log: Logger = LoggerFactory.getLogger("nav.OppgaveApisNy")

//TODO generell sikring kode6 - se etter feil
//TODO fjern reservasjonsid fra objekter til frontend

internal fun Route.ReservasjonApisNy() {
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val reservasjonApisTjeneste by inject<ReservasjonApisTjeneste>()

    post("/reserver", {
        description = "Reserver en oppgave direkte, uten å gå via en oppgavekø."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            body<OppgaveIdMedOverstyringDto> {
                description = "Oppgaven som skal reserveres, evt. med overstyring av sjekker/begrunnelse"
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            val skjermet = kontekst.harTilgangTilKode6()
            if (kontekst.harTilgangTilReserveringAvOppgaver()) {
                val område = kontekst.område
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

    get("/reserverte", {
        description = "Hent alle aktive reservasjoner for innlogget saksbehandler."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            val skjermet = kontekst.harTilgangTilKode6()
            if (kontekst.harBasisTilgang()) {
                val område = kontekst.område
                val innloggetBrukerNavIdent = kontekst.bruker.navIdent
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(innloggetBrukerNavIdent, skjermet)

                if (innloggetBruker != null) {
                    val reservasjoner = reservasjonApisTjeneste.hentReserverteOppgaverSammendragForSaksbehandler(innloggetBruker, kontekst)
                    call.respond(reservasjoner)
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
        description = "Opphev en eller flere aktive reservasjoner."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            body<List<AnnullerReservasjonDto>> {
                description = "Reservasjonene som skal oppheves"
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            val skjermet = kontekst.harTilgangTilKode6()
            if (kontekst.harBasisTilgang()) {
                val område = kontekst.område
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

    post("/forleng", {
        description = "Forleng en aktiv reservasjon til en ny dato."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            body<ForlengReservasjonDto> {
                description = "Reservasjonen som skal forlenges, og ny til-dato"
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            val skjermet = kontekst.harTilgangTilKode6()
            if (kontekst.harBasisTilgang()) {
                val område = kontekst.område
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

    post("/flytt", {
        description = "Overfør en aktiv reservasjon til en annen saksbehandler."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            body<FlyttReservasjonDto> {
                description = "Reservasjonen som skal flyttes, hvem den skal flyttes til, og begrunnelse"
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            val skjermet = kontekst.harTilgangTilKode6()
            if (kontekst.harBasisTilgang()) {
                val område = kontekst.område
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

    post("/reservasjon/endre", {
        description = "Endre en eller flere aktive reservasjoner, f.eks. hvem som eier den, til-dato eller begrunnelse."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            body<List<ReservasjonEndringDto>> {
                description = "Endringene som skal utføres på reservasjonene"
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            val skjermet = kontekst.harTilgangTilKode6()
            if (kontekst.harBasisTilgang()) {
                val område = kontekst.område
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

    post("/flytt/sok", {
        description = "Søk etter en saksbehandler basert på brukerident, for bruk ved flytting av reservasjon."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            body<BrukerIdentDto> {
                description = "Brukerident det skal søkes etter"
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            val skjermet = kontekst.harTilgangTilKode6()
            if (kontekst.harBasisTilgang()) {
                val params = call.receive<BrukerIdentDto>()
                val sokSaksbehandlerMedIdent = saksbehandlerRepository.sokSaksbehandler(params.brukerIdent, kontekst.område, skjermet)
                call.respond(sokSaksbehandlerMedIdent)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/saksbehandlere", {
        description = "Hent alle saksbehandlere."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            val skjermet = kontekst.harTilgangTilKode6()
            if (kontekst.harBasisTilgang()) {
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

    get("/aktiv-reservasjon", {
        description = "Hent aktiv reservasjon for en gitt oppgave, dersom den finnes."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            queryParameter<String>("oppgaveEksternId") {
                description = "Oppgavens eksterne id"
                required = true
            }
            queryParameter<String>("oppgaveTypeEksternId") {
                description = "Ekstern id for oppgavetypen"
                required = true
            }
            queryParameter<String>("områdeEksternId") {
                description = "Ekstern id for området oppgaven tilhører"
                required = true
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (kontekst.harBasisTilgang()) {
                val område = kontekst.område
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
    get("/alle-reservasjoner", {
        description = "Hent alle aktive reservasjoner."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (kontekst.erOppgavestyrer()) {
                call.respond(reservasjonApisTjeneste.hentAlleAktiveReservasjoner(kontekst))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}




