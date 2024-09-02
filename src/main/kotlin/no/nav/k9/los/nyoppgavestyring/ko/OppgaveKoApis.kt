package no.nav.k9.los.nyoppgavestyring.ko

import io.ktor.http.*
import io.ktor.server.application.call
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.integrasjon.rest.idToken
import no.nav.k9.los.nyoppgavestyring.ko.dto.*
import no.nav.k9.los.utils.OpentelemetrySpanUtil
import org.koin.java.KoinJavaComponent
import org.koin.ktor.ext.inject

fun Route.OppgaveKoApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by KoinJavaComponent.inject<IPepClient>(IPepClient::class.java)

    get("/") {
        requestContextService.withRequestContext(call) {
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden)
            }

            val harTilgangTilKode6 = pepClient.harTilgangTilKode6()
            val oppgavekøer = oppgaveKoTjeneste.hentOppgavekøer(kode6 = harTilgangTilKode6)
                .map { oppgaveko ->
                    OppgaveKoListeelement(
                        id = oppgaveko.id,
                        tittel = oppgaveko.tittel,
                        antallSaksbehandlere = oppgaveko.saksbehandlere.size,
                        sistEndret = oppgaveko.endretTidspunkt
                    )
                }

            call.respond(OppgaveKoListeDto(oppgavekøer))
        }
    }

    post("/kopier") {
        val kopierOppgaveKoDto = call.receive<KopierOppgaveKoDto>()
        requestContextService.withRequestContext(call) {
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden)
            }
            call.respond(
                oppgaveKoTjeneste.kopier(
                    kopierOppgaveKoDto.kopierFraOppgaveId,
                    kopierOppgaveKoDto.tittel,
                    kopierOppgaveKoDto.taMedQuery,
                    kopierOppgaveKoDto.taMedSaksbehandlere
                )
            )
        }
    }

    post("/opprett") {
        val opprettOppgaveKoDto = call.receive<OpprettOppgaveKoDto>()
        requestContextService.withRequestContext(call) {
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden)
            }
            call.respond(oppgaveKoTjeneste.leggTil(opprettOppgaveKoDto.tittel))
        }
    }

    get("/{id}") {
        requestContextService.withRequestContext(call) {
            val oppgavekøId = call.parameters["id"]!!
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden)
            }
            call.respond(oppgaveKoTjeneste.hent(oppgavekøId.toLong()))
        }
    }

    delete("/{id}") {
        requestContextService.withRequestContext(call) {
            val oppgavekøId = call.parameters["id"]!!
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden)
            }

            call.respond(oppgaveKoTjeneste.slett(oppgavekøId.toLong()))
        }
    }

    get("/saksbehandlerskoer") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        kotlin.coroutines.coroutineContext.idToken().getUsername()
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/oppgaver") {
        requestContextService.withRequestContext(call) {
            val oppgavekøId = call.parameters["id"]!!
            if (pepClient.harTilgangTilReservingAvOppgaver()) {
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
            val oppgavekøId = call.parameters["id"]!!
            call.respond(
                oppgaveKoTjeneste.hentSaksbehandlereForKo(oppgavekøId.toLong())
            )
        }
    }

    get("/{id}/antall-oppgaver") {
        requestContextService.withRequestContext(call) {
            val oppgavekøId = call.parameters["id"]!!
            val filtrerReserverte = call.request.queryParameters["filtrer_reserverte"]?.toBoolean() ?: true

            val antall = OpentelemetrySpanUtil.spanSuspend("OppgaveKoTjeneste.hentAntallOppgaverForKø") {
                oppgaveKoTjeneste.hentAntallOppgaverForKø(
                    oppgavekøId.toLong(),
                    filtrerReserverte
                )
            }
            call.respond(antall)
        }
    }

    post("/{id}/fa-oppgave") {
        requestContextService.withRequestContext(call) {
            val oppgavekøId = call.parameters["id"]!!

            val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedEpost(
                kotlin.coroutines.coroutineContext.idToken().getUsername()
            )!!

            if (pepClient.harTilgangTilReservingAvOppgaver()) {
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
        val oppgaveKo = call.receive<OppgaveKo>()
        requestContextService.withRequestContext(call) {
            if (!pepClient.erOppgaveStyrer()) {
                call.respond(HttpStatusCode.Forbidden)
            }

            call.respond(oppgaveKoTjeneste.endre(oppgaveKo))
        }
    }
}