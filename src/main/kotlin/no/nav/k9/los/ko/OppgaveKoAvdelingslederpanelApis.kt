package no.nav.k9.los.ko

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.ko.dto.KopierOppgaveKoDto
import no.nav.k9.los.ko.dto.OppgaveKo
import no.nav.k9.los.ko.dto.OppgaveKoIdOgTittel
import no.nav.k9.los.ko.dto.OppgaveKoListeDto
import no.nav.k9.los.ko.dto.OppgaveKoListeelement
import no.nav.k9.los.ko.dto.OpprettOppgaveKoDto
import no.nav.k9.los.ko.dto.SaksbehandlerForKolisteDto
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject
import kotlin.text.toLong

fun Route.OppgaveKoAvdelingslederApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by inject<IPepClient>()

    get("/hentKoliste") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgavekøer = oppgaveKoTjeneste.hentOppgavekøer(skjermet = pepClient.harTilgangTilKode6())
                    .map { oppgaveko ->
                        OppgaveKoListeelement(
                            id = oppgaveko.id,
                            tittel = oppgaveko.tittel,
                            antallSaksbehandlere = oppgaveko.saksbehandlere.size,
                            sistEndret = oppgaveko.endretTidspunkt
                        )
                    }

                call.respond(OppgaveKoListeDto(oppgavekøer))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/endreKo") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgaveKo = call.receive<OppgaveKo>()
                call.respond(oppgaveKoTjeneste.endre(oppgaveKo, pepClient.harTilgangTilKode6()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/kopier") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val kopierOppgaveKoDto = call.receive<KopierOppgaveKoDto>()
                call.respond(
                    oppgaveKoTjeneste.kopier(
                        kopierOppgaveKoDto.kopierFraOppgaveId,
                        kopierOppgaveKoDto.tittel,
                        kopierOppgaveKoDto.taMedQuery,
                        kopierOppgaveKoDto.taMedSaksbehandlere,
                        pepClient.harTilgangTilKode6()
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/saksbehandlere") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val alleSaksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere()
                    .map { saksbehandler ->
                        SaksbehandlerForKolisteDto(saksbehandler)
                    }
                call.respond(alleSaksbehandlere)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/opprett") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val opprettOppgaveKoDto = call.receive<OpprettOppgaveKoDto>()
                val harSkjermetTilgang = pepClient.harTilgangTilKode6()
                // OpprettOppgaveKoDto bærer ikke område ennå. Skal det opprettes køer utenfor K9,
                // må feltet inn i DTO-en og settes av klienten.
                call.respond(
                    oppgaveKoTjeneste.leggTil(
                        opprettOppgaveKoDto.tittel,
                        skjermet = harSkjermetTilgang,
                        område = Områder.K9
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(oppgaveKoTjeneste.hent(oppgavekøId.toLong(), pepClient.harTilgangTilKode6()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    delete("/{id}") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(oppgaveKoTjeneste.slett(oppgavekøId.toLong()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/antall") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgavekøId = call.parameters["id"]!!
                val skjermet = pepClient.harTilgangTilKode6()
                call.respond(oppgaveKoTjeneste.hentAntallMedOgUtenReserverteForKø(oppgavekøId.toLong(), skjermet))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/andre-saksbehandleres-koer") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        call.parameters["id"]?.toLong()!!,
                        pepClient.harTilgangTilKode6()
                    ).map {
                        OppgaveKoIdOgTittel(
                            id = it.id,
                            tittel = it.tittel
                        )
                    }
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

}