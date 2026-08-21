package no.nav.k9.los.ko

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.ko.dto.*
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

//TODO: Splitte i OppgaveKoAvdelingslederpanelApis og OppgaveKoSaksbehandlerpanelApis

fun Route.OppgaveKoApis() {
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()

    get("/") {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer) {
                val oppgavekøer = oppgaveKoTjeneste.hentOppgavekøer(skjermet = bruker.harTilgangTilKode6)
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
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer) {
                val kopierOppgaveKoDto = call.receive<KopierOppgaveKoDto>()
                call.respond(
                    oppgaveKoTjeneste.kopier(
                        kopierOppgaveKoDto.kopierFraOppgaveId,
                        kopierOppgaveKoDto.tittel,
                        kopierOppgaveKoDto.taMedQuery,
                        kopierOppgaveKoDto.taMedSaksbehandlere,
                        bruker.harTilgangTilKode6
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/saksbehandlere") {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer) {
                val alleSaksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere(bruker.område, bruker.harTilgangTilKode6)
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
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer) {
                val opprettOppgaveKoDto = call.receive<OpprettOppgaveKoDto>()
                val harSkjermetTilgang = bruker.harTilgangTilKode6
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
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(oppgaveKoTjeneste.hent(oppgavekøId.toLong(), bruker.harTilgangTilKode6))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    delete("/{id}") {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(oppgaveKoTjeneste.slett(oppgavekøId.toLong()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/saksbehandlerskoer") {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang) {
                val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    bruker.navIdent,
                    bruker.harTilgangTilKode6
                )!!
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        saksbehandler.id!!,
                        bruker.harTilgangTilKode6
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/andre-saksbehandleres-koer") {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer) {
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        call.parameters["id"]?.toLong()!!,
                        bruker.harTilgangTilKode6
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
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentOppgaverFraKø(
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


    get("/{id}/saksbehandlere") {
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

    get("/{id}/antall") {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang) {
                val oppgavekøId = call.parameters["id"]!!
                val skjermet = bruker.harTilgangTilKode6
                call.respond(oppgaveKoTjeneste.hentAntallMedOgUtenReserverteForKø(oppgavekøId.toLong(), skjermet))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/antall-uten-reserverte") {
        medBrukerkontekst { bruker ->
            if (bruker.harBasisTilgang) {
                val oppgavekøId = call.parameters["id"]!!
                val skjermet = bruker.harTilgangTilKode6
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

    post("") {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer) {
                val oppgaveKo = call.receive<OppgaveKo>()
                call.respond(oppgaveKoTjeneste.endre(oppgaveKo, bruker.harTilgangTilKode6))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
