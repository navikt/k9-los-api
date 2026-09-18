package no.nav.k9.los.ko

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.ko.dto.OppgaveKo
import no.nav.k9.los.ko.dto.ReservasjonV3FraKøDto
import no.nav.k9.los.ko.dto.SaksbehandlerForKolisteDto
import no.nav.k9.los.oppgaveuthenting.sammendrag.OppgaveSammendragDto
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

fun Route.OppgaveKoSaksbehandlerApisNy() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by inject<IPepClient>()

    get("/saksbehandlerskoer", {
        operationId = "hentSaksbehandlersOppgavekoer"
        summary = "Hent saksbehandlers oppgavekøer"
        response {
            HttpStatusCode.OK to {
                body<List<OppgaveKo>>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker mangler basistilgang" }
            HttpStatusCode.NotFound to { description = "Oppgavekøen finnes ikke for gjeldende område og skjerming" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val kode6 = pepClient.harTilgangTilKode6()
                val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    ident = coroutineContext.idToken().getNavIdent(),
                    skjermet = kode6
                )!!
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        coroutineContext.område(),
                        kode6,
                        saksbehandler.id,
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/oppgaver-i-koen", {
        operationId = "hentOppgaverISaksbehandlerko"
        summary = "Hent oppgaver i oppgavekø"
        request {
            pathParameter<Long>("id") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "Inntil ti tilgjengelige oppgaver fra oppgavekøen"
                body<List<OppgaveSammendragDto>>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker mangler basistilgang" }
            HttpStatusCode.NotFound to { description = "Oppgavekøen finnes ikke for gjeldende område og skjerming" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentOppgaverFraKøSammendrag(
                        coroutineContext.område(),
                        pepClient.harTilgangTilKode6(),
                        coroutineContext.idToken(),
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

    get("/{id}/koens-saksbehandlere", {
        operationId = "hentSaksbehandlereISaksbehandlerko"
        summary = "Hent medlemmer av oppgavekø"
        request {
            pathParameter<Long>("id") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                body<List<SaksbehandlerForKolisteDto>>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker mangler basistilgang" }
            HttpStatusCode.NotFound to { description = "Oppgavekøen finnes ikke for gjeldende område og skjerming" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentSaksbehandlereForKo(
                        område = coroutineContext.område(),
                        kode6 = pepClient.harTilgangTilKode6(),
                        oppgaveKoId = oppgavekøId.toLong()
                    ).map { SaksbehandlerForKolisteDto(it) }
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/antall-uten-reserverte", {
        operationId = "hentAntallOppgaverUtenReserverteISaksbehandlerko"
        summary = "Hent antall ledige oppgaver"
        request {
            pathParameter<Long>("id") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                body<AntallOppgaver>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker mangler basistilgang" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val oppgavekøId = call.parameters["id"]!!
                val kode6 = pepClient.harTilgangTilKode6()
                val antallUtenReserverte = OpentelemetrySpanUtil.span("OppgaveKoTjeneste.hentAntallOppgaverForKø") {
                    oppgaveKoTjeneste.hentAntallOppgaverForKø(
                        område = coroutineContext.område(),
                        skjermet = kode6,
                        oppgaveKoId = oppgavekøId.toLong(),
                        filtrerReserverte = true,
                    )
                }
                call.respond(AntallOppgaver(antallUtenReserverte))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/{id}/fa-oppgave", {
        operationId = "reserverNesteOppgaveFraSaksbehandlerko"
        summary = "Reserver neste oppgave"
        request {
            pathParameter<Long>("id") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                description = "En liste med reservasjonen, eller en tom liste dersom ingen oppgave kunne reserveres"
                body<List<ReservasjonV3FraKøDto>>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker mangler tilgang til å reservere oppgaver" }
            HttpStatusCode.NotFound to { description = "Oppgavekøen finnes ikke for gjeldende område og skjerming" }
        }
    }) {
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
}
