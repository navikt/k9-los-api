package no.nav.k9.los.ko

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.kontekst.medBrukerkontekst
import no.nav.k9.los.infrastruktur.kontekst.Brukerkontekst
import no.nav.k9.los.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.område
import org.koin.ktor.ext.inject

fun Route.OppgaveKoSaksbehandlerApisNy() {
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by inject<IPepClient>()

    get("/saksbehandlerskoer", {
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
    }) {
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    kontekst.bruker.navIdent,
                    pepClient.erKode6Bruker(kontekst.bruker)
                )!!
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        saksbehandler.id!!,
                        pepClient.harTilgangTilKode6(kontekst)
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
        medBrukerkontekst { kontekst ->
            if (pepClient.harBasisTilgang(kontekst)) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentOppgaverFraKøSammendrag(
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
        medBrukerkontekst { kontekst ->
            if (pepClient.harTilgangTilReserveringAvOppgaver(kontekst)) {
                val oppgavekøId = call.parameters["id"]!!
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    kontekst.bruker.navIdent,
                    pepClient.erKode6Bruker(kontekst.bruker)
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


