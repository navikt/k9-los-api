package no.nav.k9.los.ko

import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.område
import org.koin.ktor.ext.inject

fun Route.OppgaveKoSaksbehandlerApisNy() {
    val requestContextService by inject<RequestContextService>()
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
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val område = call.område // TODO: bruk område når tjenesten er oppdatert til å ta hensyn til område
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
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val område = call.område // TODO: bruk område når tjenesten er oppdatert til å ta hensyn til område
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentOppgaverFraKøSammendrag(
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
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val område = call.område // TODO: bruk område når tjenesten er oppdatert til å ta hensyn til område
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentSaksbehandlereForKo(oppgavekøId.toLong())
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
        requestContextService.withRequestContext(call) {
            if (pepClient.harBasisTilgang()) {
                val område = call.område // TODO: bruk område når tjenesten er oppdatert til å ta hensyn til område
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
        requestContextService.withRequestContext(call) {
            if (pepClient.harTilgangTilReserveringAvOppgaver()) {
                val område = call.område // TODO: bruk område når tjenesten er oppdatert til å ta hensyn til område
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




