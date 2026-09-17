package no.nav.k9.los.ko

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.ko.dto.*
import no.nav.k9.los.infrastruktur.utils.OpentelemetrySpanUtil
import org.koin.ktor.ext.inject

fun Route.OppgaveKoApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by inject<IPepClient>()

    get("/") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgavekøer = oppgaveKoTjeneste.hentOppgavekøer(område = coroutineContext.område(), skjermet = pepClient.harTilgangTilKode6())
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
                        coroutineContext.område(),
                        pepClient.harTilgangTilKode6(),
                        kopierOppgaveKoDto.kopierFraOppgaveId,
                        kopierOppgaveKoDto.tittel,
                        kopierOppgaveKoDto.taMedQuery,
                        kopierOppgaveKoDto.taMedSaksbehandlere
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
                val alleSaksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere(
                    område = coroutineContext.område(),
                    skjermet = pepClient.harTilgangTilKode6()
                ).map { saksbehandler -> SaksbehandlerForKolisteDto(saksbehandler) }
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
                call.respond(oppgaveKoTjeneste.leggTil(
                    område = coroutineContext.område(),
                    skjermet = harSkjermetTilgang,
                    tittel = opprettOppgaveKoDto.tittel
                ))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(oppgaveKoTjeneste.hent(coroutineContext.område(), pepClient.harTilgangTilKode6(), oppgavekøId.toLong()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    delete("/{id}") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(oppgaveKoTjeneste.slett(coroutineContext.område(), pepClient.harTilgangTilKode6(),oppgavekøId.toLong()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/saksbehandlerskoer") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    coroutineContext.idToken().getNavIdent(),
                    pepClient.harTilgangTilKode6()
                )!!
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        coroutineContext.område(),
                        pepClient.harTilgangTilKode6(),
                        saksbehandler.id,
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
                        coroutineContext.område(),
                        pepClient.harTilgangTilKode6(),
                        call.parameters["id"]?.toLong()!!,
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
            if (pepClient.harBasisTilgang()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentOppgaverFraKø(
                        område = coroutineContext.område(),
                        idToken = coroutineContext.idToken(),
                        oppgaveKoId = oppgavekøId.toLong(),
                        ønsketAntallOppgaver = 10,
                        fjernReserverte = true
                    )
                )
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
                    oppgaveKoTjeneste.hentSaksbehandlereForKo(
                        coroutineContext.område(),
                        pepClient.harTilgangTilKode6(),
                        oppgavekøId.toLong()
                    )
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
                call.respond(oppgaveKoTjeneste.hentAntallMedOgUtenReserverteForKø(
                    område = coroutineContext.område(),
                    oppgaveKoId = oppgavekøId.toLong(),
                    skjermet = skjermet
                ))
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
                            område = coroutineContext.område(),
                            oppgaveKoId = oppgavekøId.toLong(),
                            filtrerReserverte = true,
                            skjermet = skjermet
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
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    coroutineContext.idToken().getNavIdent(),
                    pepClient.harTilgangTilKode6()
                )!!
                val oppgaveMuligReservert = oppgaveKoTjeneste.taReservasjonFraKø(
                    område = coroutineContext.område(),
                    innloggetBrukerId = innloggetBruker.id,
                    oppgaveKoId = oppgavekøId.toLong(),
                )
                call.respond(
                    when (oppgaveMuligReservert) {
                        is OppgaveMuligReservert.Reservert -> listOf(
                            ReservasjonV3FraKøDto(
                                oppgaveMuligReservert.reservasjon,
                                oppgaveMuligReservert.oppgave,
                                innloggetBruker
                            )
                        )
                        OppgaveMuligReservert.IkkeReservert -> emptyList()
                    }
                )
            } else {
                call.respond(HttpStatusCode.Forbidden, "Innlogget bruker mangler tilgang til å reservere oppgaver")
            }
        }
    }

    post("") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgaveKo = call.receive<OppgaveKo>()
                call.respond(oppgaveKoTjeneste.endre(
                    område = coroutineContext.område(),
                    skjermet = pepClient.harTilgangTilKode6(),
                    oppgaveKo = oppgaveKo
                ))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
