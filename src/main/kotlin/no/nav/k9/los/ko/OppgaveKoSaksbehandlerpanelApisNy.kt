package no.nav.k9.los.ko

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

fun Route.OppgaveKoSaksbehandlerApisNy() {
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()

    get("/saksbehandlerskoer", {
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
    }) {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang) {
                val harTilgangTilKode6 = bruker.harTilgangTilKode6
                val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    bruker.navIdent,
                    harTilgangTilKode6
                )!!
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        saksbehandler.id!!,
                        harTilgangTilKode6,
                        bruker.område
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/oppgaver", {
        description = "Hent oppgaver i en oppgavekø, uten reserverte oppgaver."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til oppgavekøen"
            }
        }
    }) {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentOppgaverFraKøSammendrag(
                        bruker,
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

    get("/{id}/saksbehandlere", {
        description = "Hent saksbehandlere som er medlem av en oppgavekø."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til oppgavekøen"
            }
        }
    }) {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentSaksbehandlereForKo(oppgavekøId.toLong(), bruker)
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/antall-uten-reserverte", {
        description = "Hent antall oppgaver i en oppgavekø, uten reserverte oppgaver."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til oppgavekøen"
            }
        }
    }) {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang) {
                val oppgavekøId = call.parameters["id"]!!
                val skjermet = bruker.harTilgangTilKode6
                val antallUtenReserverte = OpentelemetrySpanUtil.span("OppgaveKoTjeneste.hentAntallOppgaverForKø") {
                    oppgaveKoTjeneste.hentAntallOppgaverForKø(
                        oppgaveKoId = oppgavekøId.toLong(),
                        filtrerReserverte = true,
                        skjermet = skjermet,
                        område = bruker.område
                    )
                }
                call.respond(AntallOppgaver(antallUtenReserverte))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/{id}/fa-oppgave", {
        description = "Reserver neste ledige oppgave fra en oppgavekø til innlogget saksbehandler."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            pathParameter<Long>("id") {
                description = "Id til oppgavekøen"
            }
        }
    }) {
        medBrukerkontekst { bruker ->
            if (bruker.harTilgangTilReserveringAvOppgaver) {
                val oppgavekøId = call.parameters["id"]!!
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    bruker.navIdent,
                    bruker.harTilgangTilKode6
                )!!
                val oppgaveMuligReservert = oppgaveKoTjeneste.taReservasjonFraKø(
                    innloggetBrukerId = innloggetBruker.id!!,
                    oppgaveKoId = oppgavekøId.toLong(),
                    bruker
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

