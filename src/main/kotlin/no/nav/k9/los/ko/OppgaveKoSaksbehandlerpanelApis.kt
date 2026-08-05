package no.nav.k9.los.ko

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

fun Route.OppgaveKoSaksbehandlerApis() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by inject<IPepClient>()

    get("/saksbehandlerskoer") {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    kotlin.coroutines.coroutineContext.idToken().getNavIdent()
                )!!
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        saksbehandler.id!!,
                        pepClient.harTilgangTilKode6()
                    )
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
                        oppgavekøId.toLong(),
                        10,
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
                    oppgaveKoTjeneste.hentSaksbehandlereForKo(oppgavekøId.toLong())
                )
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
                    kotlin.coroutines.coroutineContext.idToken().getNavIdent()
                )!!
                val oppgaveMuligReservert = oppgaveKoTjeneste.taReservasjonFraKø(
                    innloggetBrukerId = innloggetBruker.id!!,
                    oppgaveKoId = oppgavekøId.toLong(),
                    kotlin.coroutines.coroutineContext
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
}