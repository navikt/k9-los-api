package no.nav.k9.los.ko

import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

fun Route.OppgaveKoSaksbehandlerApis() {
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by inject<IPepClient>()

    get("/saksbehandlerskoer") {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val harTilgangTilKode6 = pepClient.harTilgangTilKode6(kontekst)
                val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    kontekst.bruker.navIdent,
                    harTilgangTilKode6
                )!!
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        saksbehandler.id!!,
                        harTilgangTilKode6
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/oppgaver") {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentOppgaverFraKø(
                        kontekst,
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
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentSaksbehandlereForKo(oppgavekøId.toLong(), kontekst)
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/antall-uten-reserverte") {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val oppgavekøId = call.parameters["id"]!!
                val skjermet = pepClient.harTilgangTilKode6(kontekst)
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
        medBrukerkontekst { kontekst ->
            if (pepClient.harTilgangTilReserveringAvOppgaver(kontekst)) {
                val oppgavekøId = call.parameters["id"]!!
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    kontekst.bruker.navIdent,
                    pepClient.harTilgangTilKode6(kontekst)
                )!!
                val oppgaveMuligReservert = oppgaveKoTjeneste.taReservasjonFraKø(
                    innloggetBrukerId = innloggetBruker.id!!,
                    oppgaveKoId = oppgavekøId.toLong(),
                    kontekst
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
