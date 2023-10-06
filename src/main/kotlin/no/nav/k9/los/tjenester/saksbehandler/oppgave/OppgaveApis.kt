package no.nav.k9.los.tjenester.saksbehandler.oppgave

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.locations.post
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveKoTjeneste
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.*

private val log: Logger = LoggerFactory.getLogger("nav.OppgaveApis")

internal fun Route.OppgaveApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveTjeneste by inject<OppgaveTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val reservasjonV3Tjeneste by inject<ReservasjonV3Tjeneste>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val oppgaveV3Repository by inject<OppgaveRepository>()
    val oppgaveV3Tjeneste by inject<no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveTjeneste>()
    val transactionalManager by inject<TransactionalManager>()
    val pepClient by inject<IPepClient>()

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

    @Location("/behandlede") //WIP: siste behandlede oppgaver av saksbehandler. Gjenbruke gammel løsning?
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
            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!

            val reservasjoner =
                reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(innloggetBruker.id!!)

            val reservasjonV3Dtos = reservasjoner.map { reservasjon ->
                val oppgaverForReservasjonsnøkkel =
                    oppgaveV3Tjeneste.hentÅpneOppgaverForReservasjonsnøkkel(reservasjon.reservasjonsnøkkel)

                ReservasjonV3Dto(
                    reservasjon,
                    oppgaverForReservasjonsnøkkel,
                    innloggetBruker
                )
            }
            call.respond(reservasjonV3Dtos)
        }
    }

    @Location("/antall") //TODO avklare med Stian/Bjørnar -- OppgaveQueryService.query med tom select i query?
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
        /*
         1. Prøv å reservere i V3
         2. Hvis vi får reservasjon i V3 - fjern reservasjoner i V1 og returner V3-status ?? under forutsetning av at hentReservasjon sjekker V3 først
         3. Hvis vi ikke får reservasjon i V3 - reserver i V1. V1 er master på disse.

         1. Prøv å reservere i V3, dersom det er en oppgavetype V3 støtter
         2. Hvis V3 ikke støtter -- reserver i V1 og kortslutt funksjonen
         3. Hvis vi får reservasjon i V3 - fjern reservasjoner i V1 og returner V3-status
         4. Hvis vi ikke får reservasjon i V3 - reserver i V1. V1 er master på disse.

         1. Reserver begge i parallell
         2. Returner status fra V3 som master, forutsatt at oppgavetypen er støttet
         3. Ellers returner status fra V1

         1. Kjør kall mot V3
         2. Returner status fra V3
         */

        val reserverFra = LocalDateTime.now()
        val oppgaveIdMedOverstyring = call.receive<OppgaveIdMedOverstyring>()
        requestContextService.withRequestContext(call) {
            if (!pepClient.harTilgangTilReservingAvOppgaver()) {
                call.respond("") //TODO OppgaveStatusDto med null-felter
            } else {

                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                    kotlin.coroutines.coroutineContext.idToken().getUsername()
                )!!

                val oppgaveStatusDto = oppgaveTjeneste.reserverOppgave(
                    innloggetBruker.brukerIdent!!,
                    oppgaveIdMedOverstyring.overstyrIdent,
                    UUID.fromString(oppgaveIdMedOverstyring.oppgaveId),
                    oppgaveIdMedOverstyring.overstyrSjekk,
                    oppgaveIdMedOverstyring.overstyrBegrunnelse
                )

                val oppgaveV3 = transactionalManager.transaction { tx ->
                    oppgaveV3Repository.hentNyesteOppgaveForEksternId(tx, oppgaveIdMedOverstyring.oppgaveId)
                }

                val reserverForIdent = oppgaveIdMedOverstyring.overstyrIdent ?: innloggetBruker.brukerIdent
                val reserverForSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(reserverForIdent!!)!!
                //TODO: try-catch på denne, og hent eksisterende reservasjon og returner hvis man kolliderer pga samtidighet
                val reservasjonV3 = reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
                    reservasjonsnøkkel = oppgaveV3.reservasjonsnøkkel,
                    reserverForId = reserverForSaksbehandler.id!!,
                    gyldigFra = reserverFra,
                    utføresAvId = innloggetBruker.id!!,
                    kommentar = oppgaveIdMedOverstyring.overstyrBegrunnelse ?: "",
                    gyldigTil = reserverFra.plusHours(24).forskyvReservasjonsDato()
                )
                //TODO: konsistenssjekk mellom tjenesteversjonene --- V3 som master der V3 har oppgave
                //TODO: sjekke statusobjekt, saksbehandler som holder reservasjon -- feks conflict hvis noen andre hadde reservasjon fra før
                val saksbehandlerSomHarReservasjon =
                    saksbehandlerRepository.finnSaksbehandlerMedId(reservasjonV3.reservertAv)
                call.respond(
                    OppgaveStatusDto(
                        erReservert = true,
                        reservertTilTidspunkt = reservasjonV3.gyldigTil,
                        erReservertAvInnloggetBruker = reservasjonV3.reservertAv == innloggetBruker.id!!,
                        reservertAv = saksbehandlerSomHarReservasjon.brukerIdent,
                        reservertAvNavn = saksbehandlerSomHarReservasjon.navn,
                        flyttetReservasjon = null,
                        kanOverstyres = reservasjonV3.reservertAv != innloggetBruker.id!!
                    )
                )
            }
        }
    }

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
                //log.info("RESERVASJONDEBUG: Lagt til ${innloggetBruker.brukerIdent} oppgave=${oppgaveFraKø.eksternId}, beslutter=${oppgaveFraKø.tilBeslutter}, kø=${params.oppgaveKøId} (neste oppgave)")
                call.respond(ReservasjonV3FraKøDto(reservasjonFraKø, reservertOppgave!!, innloggetBruker))
            } else {
                call.respond(HttpStatusCode.NotFound, "Fant ingen oppgave i valgt kø")
            }
        }
    }

    @Location("/fa-oppgave-fra-ko")
    class fåOppgaveFraKø
    post { _: fåOppgaveFraKø ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveKøIdDto>()

            val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!

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
            val gammelReturverdi =
                oppgaveTjeneste.frigiReservasjon(UUID.fromString(params.oppgaveId), params.begrunnelse)

            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!

            val oppgave = oppgaveV3Tjeneste.hentOppgave(params.oppgaveId)
            reservasjonV3Tjeneste.annullerReservasjon(
                oppgave.reservasjonsnøkkel,
                params.begrunnelse,
                innloggetBruker.id!!
            )
            call.respond(HttpStatusCode.OK) //TODO: Hva er evt meningsfullt å returnere her?
        }
    }

    @Location("/oppgaver-på-samme-bruker") //TODO: Ikke i bruk?
    class oppgaverPåSammeBruker
    post { _: opphevReservasjon ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveId>()
            call.respond(oppgaveTjeneste.aktiveOppgaverPåSammeBruker(UUID.fromString(params.oppgaveId)))
        }
    }

    @Location("/legg-til-behandlet-sak")  //TODO: ny løsning for siste saker?
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

    @Location("/forleng")  //TODO maks 1 uke inntil videre?
    class forlengReservasjon
    post { _: forlengReservasjon ->
        requestContextService.withRequestContext(call) {
            val forlengReservasjonDto = call.receive<ForlengReservasjonDto>()
            val gammelReturverdi =
                oppgaveTjeneste.forlengReservasjonPåOppgave(UUID.fromString(forlengReservasjonDto.oppgaveId))

            //TODO oppgaveId er behandlingsUUID?
            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!
            val oppgave = oppgaveV3Tjeneste.hentOppgave(forlengReservasjonDto.oppgaveId)

            val forlengetReservasjon = try {
                reservasjonV3Tjeneste.forlengReservasjon(
                    reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                    nyTildato = forlengReservasjonDto.nyTilDato,
                    utførtAvBrukerId = innloggetBruker.id!!,
                    kommentar = forlengReservasjonDto.kommentar ?: ""
                )
            } catch (e: FinnerIkkeDataException) {
                call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
                null //TODO: Vil denne funksjonen faktisk terminere her?
            }

            val åpneOppgaverForReservasjonsnøkkel =
                oppgaveV3Tjeneste.hentÅpneOppgaverForReservasjonsnøkkel(oppgave.reservasjonsnøkkel)
            val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(forlengetReservasjon!!.reservertAv)!!

            call.respond(
                ReservasjonV3Dto(
                    forlengetReservasjon!!,
                    åpneOppgaverForReservasjonsnøkkel,
                    reservertAv
                )
            )
        }
    }

    @Location("/flytt")
    class flyttReservasjon

    post { _: flyttReservasjon ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<FlyttReservasjonId>()
            val gammelReturVerdi = oppgaveTjeneste.flyttReservasjon(
                UUID.fromString(params.oppgaveId),
                params.brukerIdent,
                params.begrunnelse
            )

            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!
            val tilSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                params.brukerIdent
            )!!

            val oppgave = oppgaveV3Tjeneste.hentOppgave(params.oppgaveId)
            val aktivReservasjon =
                reservasjonV3Tjeneste.hentAktivReservasjonForReservasjonsnøkkel(oppgave.reservasjonsnøkkel)
            if (aktivReservasjon == null) {
                call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
            }

            val nyReservasjon = try {
                reservasjonV3Tjeneste.overførReservasjon(
                    reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                    reserverTil = aktivReservasjon!!.gyldigTil.plusHours(24).forskyvReservasjonsDato(),
                    tilSaksbehandlerId = tilSaksbehandler.id!!,
                    utførtAvBrukerId = innloggetBruker.id!!,
                    kommentar = params.begrunnelse,
                )
            } catch (e: FinnerIkkeDataException) {
                call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
                null //TODO: Vil denne funksjonen faktisk terminere her?
            }

            val åpneOppgaverForReservasjonsnøkkel =
                oppgaveV3Tjeneste.hentÅpneOppgaverForReservasjonsnøkkel(oppgave.reservasjonsnøkkel)

            call.respond(
                ReservasjonV3Dto(nyReservasjon!!, åpneOppgaverForReservasjonsnøkkel, tilSaksbehandler)
            )
        }
    }

    @Location("/reservasjon/endre")
    class endreReservasjon

    post { _: endreReservasjon ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<ReservasjonEndringDto>()
            oppgaveTjeneste.endreReservasjonPåOppgave(params)


            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!
            val tilSaksbehandler = params.brukerIdent?.let { saksbehandlerRepository.finnSaksbehandlerMedIdent(it) }

            val oppgave = oppgaveV3Tjeneste.hentOppgave(params.oppgaveId) //TODO oppgaveId er behandlingsUUID?
            val nyReservasjon = try {
                reservasjonV3Tjeneste.endreReservasjon(
                    reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
                    endretAvBrukerId = innloggetBruker.id!!,
                    nyTildato = params.reserverTil?.let { LocalDateTime.of(params.reserverTil, LocalTime.MAX) },
                    nySaksbehandlerId = tilSaksbehandler?.id,
                    kommentar = params.begrunnelse
                )
            } catch (e: FinnerIkkeDataException) {
                call.respond(HttpStatusCode.NotFound, "Fant ingen aktiv reservasjon for angitt reservasjonsnøkkel")
                null //TODO: Vil denne funksjonen faktisk terminere her?
            }

            val åpneOppgaverForReservasjonsnøkkel =
                oppgaveV3Tjeneste.hentÅpneOppgaverForReservasjonsnøkkel(oppgave.reservasjonsnøkkel)
            val reservertAv = saksbehandlerRepository.finnSaksbehandlerMedId(nyReservasjon!!.reservertAv)

            call.respond(
                ReservasjonV3Dto(nyReservasjon!!, åpneOppgaverForReservasjonsnøkkel, reservertAv)
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

    @Location("/hent-historiske-reservasjoner-på-oppgave") //TODO: Ikke i bruk?
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

    @Location("/oppgaver-for-fagsaker") //TODO: Ikke i bruk?
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
