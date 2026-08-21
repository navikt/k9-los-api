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
        medBrukerkontekst { kontekst ->
            if (kontekst.erOppgavestyrer()) {
                val oppgavekøer = oppgaveKoTjeneste.hentOppgavekøer(skjermet = kontekst.harTilgangTilKode6())
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
        medBrukerkontekst { kontekst ->
            if (kontekst.erOppgavestyrer()) {
                val kopierOppgaveKoDto = call.receive<KopierOppgaveKoDto>()
                call.respond(
                    oppgaveKoTjeneste.kopier(
                        kopierOppgaveKoDto.kopierFraOppgaveId,
                        kopierOppgaveKoDto.tittel,
                        kopierOppgaveKoDto.taMedQuery,
                        kopierOppgaveKoDto.taMedSaksbehandlere,
                        kontekst.harTilgangTilKode6()
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/saksbehandlere") {
        medBrukerkontekst { kontekst ->
            if (kontekst.erOppgavestyrer()) {
                val alleSaksbehandlere = saksbehandlerRepository.hentAlleSaksbehandlere(kontekst.område, kontekst.harTilgangTilKode6())
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
        medBrukerkontekst { kontekst ->
            if (kontekst.erOppgavestyrer()) {
                val opprettOppgaveKoDto = call.receive<OpprettOppgaveKoDto>()
                val harSkjermetTilgang = kontekst.harTilgangTilKode6()
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
        medBrukerkontekst { kontekst ->
            if (kontekst.erOppgavestyrer()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(oppgaveKoTjeneste.hent(oppgavekøId.toLong(), kontekst.harTilgangTilKode6()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    delete("/{id}") {
        medBrukerkontekst { kontekst ->
            if (kontekst.erOppgavestyrer()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(oppgaveKoTjeneste.slett(oppgavekøId.toLong()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/saksbehandlerskoer") {
        medBrukerkontekst { kontekst ->
            if (kontekst.harBasisTilgang()) {
                val harTilgangTilKode6 = kontekst.harTilgangTilKode6()
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

    get("/andre-saksbehandleres-koer") {
        medBrukerkontekst { kontekst ->
            if (kontekst.erOppgavestyrer()) {
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        call.parameters["id"]?.toLong()!!,
                        kontekst.harTilgangTilKode6()
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
        medBrukerkontekst { kontekst ->
            if (kontekst.harBasisTilgang()) {
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
            if (kontekst.harBasisTilgang()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hentSaksbehandlereForKo(oppgavekøId.toLong(), kontekst)
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/antall") {
        medBrukerkontekst { kontekst ->
            if (kontekst.harBasisTilgang()) {
                val oppgavekøId = call.parameters["id"]!!
                val skjermet = kontekst.harTilgangTilKode6()
                call.respond(oppgaveKoTjeneste.hentAntallMedOgUtenReserverteForKø(oppgavekøId.toLong(), skjermet))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/antall-uten-reserverte") {
        medBrukerkontekst { kontekst ->
            if (kontekst.harBasisTilgang()) {
                val oppgavekøId = call.parameters["id"]!!
                val skjermet = kontekst.harTilgangTilKode6()
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
            if (kontekst.harTilgangTilReserveringAvOppgaver()) {
                val oppgavekøId = call.parameters["id"]!!
                val innloggetBruker = saksbehandlerRepository.finnSaksbehandlerMedIdent(
                    kontekst.bruker.navIdent,
                    kontekst.harTilgangTilKode6()
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

    post("") {
        medBrukerkontekst { kontekst ->
            if (kontekst.erOppgavestyrer()) {
                val oppgaveKo = call.receive<OppgaveKo>()
                call.respond(oppgaveKoTjeneste.endre(oppgaveKo, kontekst.harTilgangTilKode6()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
