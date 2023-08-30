package no.nav.k9.los.tjenester.saksbehandler.oppgave

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.locations.post
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.mottak.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.koin.ktor.ext.inject
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.*

private val log: Logger = LoggerFactory.getLogger("nav.OppgaveApis")

internal fun Route.OppgaveApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveTjeneste by inject<OppgaveTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val reservasjonV3Tjeneste by inject<ReservasjonV3Tjeneste>()
    val oppgaveV3Repository by inject<OppgaveRepository>()
    val transactionalManager by inject<TransactionalManager>()
    val pepClient by inject<IPepClient>()

    class hentOppgaver

    get { _: hentOppgaver ->
        val queryParameter = call.request.queryParameters["id"]
        requestContextService.withRequestContext(call) {
            call.respond(
                oppgaveTjeneste.hentNesteOppgaverIKø(UUID.fromString(queryParameter))
            )
        }
    }

    @Location("/behandlede")
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
            call.respond(oppgaveTjeneste.hentSisteReserverteOppgaver())
        }
    }

    @Location("/antall")
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
/*
                val reservasjonStatusDto = reservasjonV3Tjeneste.taReservasjon(
                    TaReservasjon(
                        innloggetBruker.id!!,
                        oppgaveIdMedOverstyring.overstyrIdent?.let { saksbehandlerRepository.finnIdMedEpost(it) },
                        oppgaveV3.reservasjonsnøkkel,
                        gyldigFra = reserverFra,
                        gyldigTil = reserverFra.plusHours(24).forskyvReservasjonsDato()
                    )
                )
                //TODO: konsistenssjekk mellom tjenesteversjonene --- V3 som master der V3 har oppgave
                //TODO: sjekke statusobjekt, saksbehandler som holder reservasjon -- feks conflict hvis noen andre hadde reservasjon fra før
                call.respond(
                    OppgaveStatusDto(
                        erReservert = reservasjonStatusDto.erReservert,
                        reservertTilTidspunkt = reservasjonStatusDto.reservertTilTidspunkt,
                        erReservertAvInnloggetBruker = reservasjonStatusDto.erReservertAvInnloggetBruker,
                        reservertAv = reservasjonStatusDto.reservertAvEpost,
                        reservertAvNavn = reservasjonStatusDto.reservertAvNavn,
                        flyttetReservasjon = null,
                        kanOverstyres = reser,
                        beskjed =,
                    )
                        oppgaveStatusDto
                )

 */
            }
        }
    }

    @Location("/fa-oppgave-fra-ko")
    class fåOppgaveFraKø
    post { _: fåOppgaveFraKø ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveKøIdDto>()

            val ident = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!.brukerIdent!!

            val oppgaveFraKø = oppgaveTjeneste.fåOppgaveFraKø(params.oppgaveKøId, ident)

            if (oppgaveFraKø != null) {
                log.info("RESERVASJONDEBUG: Lagt til $ident oppgave=${oppgaveFraKø.eksternId}, beslutter=${oppgaveFraKø.tilBeslutter}, kø=${params.oppgaveKøId} (neste oppgave)")
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
            call.respond(oppgaveTjeneste.frigiReservasjon(UUID.fromString(params.oppgaveId), params.begrunnelse))
        }
    }

    @Location("/oppgaver-på-samme-bruker")
    class oppgaverPåSammeBruker
    post { _: opphevReservasjon ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<OppgaveId>()
            call.respond(oppgaveTjeneste.aktiveOppgaverPåSammeBruker(UUID.fromString(params.oppgaveId)))
        }
    }

    @Location("/legg-til-behandlet-sak")
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
            val oppgaveId = call.receive<OppgaveId>()
            call.respond(oppgaveTjeneste.forlengReservasjonPåOppgave(UUID.fromString(oppgaveId.oppgaveId)))
        }
    }

    @Location("/flytt")
    class flyttReservasjon

    post { _: flyttReservasjon ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<FlyttReservasjonId>()
            call.respond(
                oppgaveTjeneste.flyttReservasjon(
                    UUID.fromString(params.oppgaveId),
                    params.brukerIdent,
                    params.begrunnelse
                )
            )
        }
    }

    @Location("/reservasjon/endre")
    class endreReservasjon

    post { _: endreReservasjon ->
        requestContextService.withRequestContext(call) {
            val params = call.receive<ReservasjonEndringDto>()
            call.respond(
                oppgaveTjeneste.endreReservasjonPåOppgave(params)
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
}
