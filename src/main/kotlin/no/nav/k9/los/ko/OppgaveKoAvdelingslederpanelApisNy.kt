package no.nav.k9.los.ko

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.ko.dto.KopierOppgaveKoDto
import no.nav.k9.los.ko.dto.OppgaveKo
import no.nav.k9.los.ko.dto.OppgaveKoIdOgTittel
import no.nav.k9.los.ko.dto.OppgaveKoListeDto
import no.nav.k9.los.ko.dto.OppgaveKoListeelement
import no.nav.k9.los.ko.dto.OpprettOppgaveKoDto
import no.nav.k9.los.ko.dto.SaksbehandlerForKolisteDto
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

fun Route.OppgaveKoAvdelingslederApisNy() {
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()

    get("/hentKoliste", {
        description = "Hent liste over alle oppgavekøer."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
        response {
            HttpStatusCode.OK to { body<OppgaveKoListeDto>() }
        }
    }) {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer) {
                val oppgavekøer = oppgaveKoTjeneste.hentOppgavekøer(område = bruker.område, skjermet = bruker.harTilgangTilKode6)
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

    post("/endreKo", {
        description = "Endre en eksisterende oppgavekø."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            body<OppgaveKo> {
                description = "Oppgavekøen med de nye verdiene"
            }
        }
    }) {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer) {
                val oppgaveKo = call.receive<OppgaveKo>()
                call.respond(oppgaveKoTjeneste.endre(oppgaveKo, bruker.harTilgangTilKode6, bruker.område))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/kopier", {
        description = "Kopier en eksisterende oppgavekø."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            body<KopierOppgaveKoDto> {
                description = "Hvilken kø som skal kopieres, ny tittel, og hva som skal tas med"
            }
        }
    }) {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer) {
                val kopierOppgaveKoDto = call.receive<KopierOppgaveKoDto>()
                call.respond(
                    oppgaveKoTjeneste.kopier(
                        kopierOppgaveKoDto.kopierFraOppgaveId,
                        kopierOppgaveKoDto.tittel,
                        kopierOppgaveKoDto.taMedQuery,
                        kopierOppgaveKoDto.taMedSaksbehandlere,
                        bruker.harTilgangTilKode6,
                        bruker.område
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/saksbehandlere", {
        description = "Hent alle saksbehandlere, for bruk ved administrasjon av oppgavekøer."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
        }
        response {
            HttpStatusCode.OK to { body<List<SaksbehandlerForKolisteDto>>() }
        }
    }) {
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

    post("/opprett", {
        description = "Opprett en ny oppgavekø."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            body<OpprettOppgaveKoDto> {
                description = "Tittel på den nye oppgavekøen"
            }
        }
    }) {
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
                        område = bruker.område
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}", {
        description = "Hent en oppgavekø."
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
            if (bruker.erOppgavestyrer) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(oppgaveKoTjeneste.hent(oppgavekøId.toLong(), bruker.harTilgangTilKode6, bruker.område))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    delete("/{id}", {
        description = "Slett en oppgavekø."
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
            if (bruker.erOppgavestyrer) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(oppgaveKoTjeneste.slett(oppgavekøId.toLong(), bruker.område))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/antall", {
        description = "Hent antall oppgaver, med og uten reserverte, for en oppgavekø."
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
            if (bruker.erOppgavestyrer) {
                val oppgavekøId = call.parameters["id"]!!
                val skjermet = bruker.harTilgangTilKode6
                call.respond(oppgaveKoTjeneste.hentAntallMedOgUtenReserverteForKø(oppgavekøId.toLong(), skjermet, bruker.område))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/andre-saksbehandleres-koer", {
        description = "Hent oppgavekøer en gitt saksbehandler er medlem av."
        request {
            pathParameter<Områder>("omrade") {
                description = "Området API-kallet gjelder for"
                example("K9") { value = Områder.K9 }
            }
            queryParameter<Long>("id") {
                description = "Id til saksbehandleren"
                required = true
            }
        }
        response {
            HttpStatusCode.OK to { body<List<OppgaveKoIdOgTittel>>() }
        }
    }) {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer) {
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        call.parameters["id"]?.toLong()!!,
                        bruker.harTilgangTilKode6,
                        bruker.område
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
}
