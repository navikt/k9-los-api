package no.nav.k9.los.nyoppgavestyring.ko

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.ko.dto.*
import no.nav.k9.los.utils.OpentelemetrySpanUtil
import org.koin.ktor.ext.inject

fun Route.OppgaveKoApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by inject<IPepClient>()

    get("/") {
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

    post("/opprett") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val opprettOppgaveKoDto = call.receive<OpprettOppgaveKoDto>()
                val harSkjermetTilgang = pepClient.harTilgangTilKode6()
                call.respond(oppgaveKoTjeneste.leggTil(opprettOppgaveKoDto.tittel, skjermet = harSkjermetTilgang))
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

    get("/saksbehandlerskoer") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        kotlin.coroutines.coroutineContext.idToken().getUsername(),
                        pepClient.harTilgangTilKode6()
                    )
                )
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

    get("/{id}/oppgaver") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harTilgangTilReserveringAvOppgaver()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentOppgaverFraKø(
                        oppgavekøId.toLong(),
                        10,
                        fjernReserverte = true
                    )
                ) //Finn et fornuftig antall?
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }


    get("/{id}/saksbehandlere") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentSaksbehandlereForKo(oppgavekøId.toLong())
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/antall") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgavekøId = call.parameters["id"]!!
                val skjermet = pepClient.harTilgangTilKode6()
                val antallUtenReserverte = OpentelemetrySpanUtil.span("OppgaveKoTjeneste.hentAntallOppgaverForKø") {
                    oppgaveKoTjeneste.hentAntallOppgaverForKø(
                        oppgavekøId.toLong(),
                        true,
                        skjermet
                    )
                }
                val antallMedReserverte = OpentelemetrySpanUtil.span("OppgaveKoTjeneste.hentAntallOppgaverForKø") {
                    oppgaveKoTjeneste.hentAntallOppgaverForKø(
                        oppgavekøId.toLong(),
                        false,
                        skjermet
                    )
                }
                call.respond(AntallOppgaverOgReserverte(antallUtenReserverte, antallMedReserverte))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/antall-uten-reserverte") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgavekøId = call.parameters["id"]!!
                val skjermet = pepClient.harTilgangTilKode6()
                val antallUtenReserverte = OpentelemetrySpanUtil.span("OppgaveKoTjeneste.hentAntallOppgaverForKø") {
                    oppgaveKoTjeneste.hentAntallOppgaverForKø(
                        oppgavekøId.toLong(),
                        true,
                        skjermet
                    )
                }
                call.respond(AntallOppgaver(antallUtenReserverte))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/{id}/fa-oppgave") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harTilgangTilReserveringAvOppgaver()) {
                val oppgavekøId = call.parameters["id"]!!
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                    kotlin.coroutines.coroutineContext.idToken().getUsername()
                )!!
                val (reservertOppgave, reservasjonFraKø) = oppgaveKoTjeneste.taReservasjonFraKø(
                    innloggetBrukerId = innloggetBruker.id!!,
                    oppgaveKoId = oppgavekøId.toLong(),
                    kotlin.coroutines.coroutineContext
                ) ?: Pair(null, null)

                if (reservasjonFraKø != null) {
                    call.respond(ReservasjonV3FraKøDto(reservasjonFraKø, reservertOppgave!!, innloggetBruker))
                } else {
                    call.respond(HttpStatusCode.NotFound, "Fant ingen oppgave i valgt kø")
                }
            } else {
                call.respond(HttpStatusCode.Forbidden, "Innlogget bruker mangler tilgang til å reservere oppgaver")
            }
        }
    }

    post("") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgaveKo = call.receive<OppgaveKo>()
                call.respond(oppgaveKoTjeneste.endre(oppgaveKo, pepClient.harTilgangTilKode6()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}