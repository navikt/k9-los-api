package no.nav.k9.los.ko

import io.github.smiley4.ktoropenapi.delete
import io.github.smiley4.ktoropenapi.get
import io.github.smiley4.ktoropenapi.post
import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.ko.dto.*
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

fun Route.OppgaveKoAvdelingslederApisNy() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveKoTjeneste by inject<OppgaveKoTjeneste>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by inject<IPepClient>()

    get("/alle-koer", {
        operationId = "hentAlleOppgavekoerForAvdelingsleder"
        summary = "Hent alle oppgavekøer"
        response {
            HttpStatusCode.OK to {
                body<List<OppgaveKoListeelement>>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker er ikke oppgavestyrer" }
            HttpStatusCode.NotFound to { description = "Oppgavekøen finnes ikke for gjeldende område og skjerming" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgavekøer = oppgaveKoTjeneste.hentOppgavekøer(
                    område = coroutineContext.område(),
                    skjermet = pepClient.harTilgangTilKode6()
                )
                    .map { oppgaveko ->
                        OppgaveKoListeelement(
                            id = oppgaveko.id,
                            tittel = oppgaveko.tittel,
                            antallSaksbehandlere = oppgaveko.saksbehandlere.size,
                            sistEndret = oppgaveko.endretTidspunkt
                        )
                    }

                call.respond(oppgavekøer)
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/endre", {
        operationId = "endreOppgavekoSomAvdelingsleder"
        summary = "Endre oppgavekø"
        request {
            body<OppgaveKo> {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                body<OppgaveKo>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker er ikke oppgavestyrer" }
            HttpStatusCode.NotFound to { description = "Oppgavekøen som skal kopieres finnes ikke for gjeldende område og skjerming" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgaveKo = call.receive<OppgaveKo>()
                call.respond(
                    oppgaveKoTjeneste.endre(
                        coroutineContext.område(),
                        pepClient.harTilgangTilKode6(),
                        oppgaveKo
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/kopier", {
        operationId = "kopierOppgavekoSomAvdelingsleder"
        summary = "Kopier oppgavekø"
        request {
            body<KopierOppgaveKoDto> {
                description = "Hvilken kø som skal kopieres, ny tittel, og hva som skal tas med"
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                body<OppgaveKo>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker er ikke oppgavestyrer" }
            HttpStatusCode.NotFound to { description = "Oppgavekøen finnes ikke for gjeldende område og skjerming" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val kopierOppgaveKoDto = call.receive<KopierOppgaveKoDto>()
                call.respond(
                    oppgaveKoTjeneste.kopier(
                        coroutineContext.område(),
                        pepClient.harTilgangTilKode6(),
                        kopierOppgaveKoDto.kopierFraOppgaveId,
                        kopierOppgaveKoDto.tittel,
                        kopierOppgaveKoDto.taMedQuery,
                        kopierOppgaveKoDto.taMedSaksbehandlere,
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/alle-saksbehandlere", {
        operationId = "hentAlleSaksbehandlereForOppgavekoadministrasjon"
        summary = "Hent alle saksbehandlere"
        response {
            HttpStatusCode.OK to {
                body<List<SaksbehandlerForKolisteDto>>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker er ikke oppgavestyrer" }
            HttpStatusCode.NotFound to { description = "Oppgavekøen finnes ikke for gjeldende område og skjerming" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(
                    saksbehandlerRepository.hentAlleSaksbehandlere(
                        område = coroutineContext.område(),
                        skjermet = pepClient.harTilgangTilKode6()
                    ).map { SaksbehandlerForKolisteDto(it) })
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/opprett", {
        operationId = "opprettOppgavekoSomAvdelingsleder"
        summary = "Opprett oppgavekø"
        request {
            body<OpprettOppgaveKoDto> {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                body<OppgaveKo>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker er ikke oppgavestyrer" }
            HttpStatusCode.NotFound to { description = "Oppgavekøen finnes ikke for gjeldende område og skjerming" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val opprettOppgaveKoDto = call.receive<OpprettOppgaveKoDto>()
                val kode6 = pepClient.harTilgangTilKode6()
                call.respond(
                    oppgaveKoTjeneste.leggTil(
                        område = coroutineContext.område(),
                        skjermet = kode6,
                        opprettOppgaveKoDto.tittel,
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}", {
        operationId = "hentOppgavekoSomAvdelingsleder"
        summary = "Hent oppgavekø"
        request {
            pathParameter<Long>("id") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                body<OppgaveKo>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker er ikke oppgavestyrer" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.hent(
                        coroutineContext.område(),
                        pepClient.harTilgangTilKode6(),
                        oppgavekøId.toLong()
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    delete("/{id}", {
        operationId = "slettOppgavekoSomAvdelingsleder"
        summary = "Slett oppgavekø"
        request {
            pathParameter<Long>("id") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                body<Unit>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker er ikke oppgavestyrer" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgavekøId = call.parameters["id"]!!
                call.respond(
                    oppgaveKoTjeneste.slett(
                        coroutineContext.område(),
                        pepClient.harTilgangTilKode6(),
                        oppgavekøId.toLong()
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/{id}/antall", {
        operationId = "hentAntallOppgaverIAvdelingslederko"
        summary = "Hent antall oppgaver"
        request {
            pathParameter<Long>("id") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                body<AntallOppgaverOgReserverte>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker er ikke oppgavestyrer" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val oppgavekøId = call.parameters["id"]!!
                val kode6 = pepClient.harTilgangTilKode6()
                call.respond(
                    oppgaveKoTjeneste.hentAntallMedOgUtenReserverteForKø(
                        coroutineContext.område(),
                        kode6,
                        oppgavekøId.toLong()
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/andre-saksbehandleres-koer", {
        operationId = "hentOppgavekoerForSaksbehandlerSomAvdelingsleder"
        summary = "Hent oppgavekøer for saksbehandler"
        request {
            queryParameter<Long>("saksbehandlerId") {
                required = true
            }
        }
        response {
            HttpStatusCode.OK to {
                body<List<OppgaveKoIdOgTittel>>()
            }
            HttpStatusCode.Forbidden to { description = "Innlogget bruker er ikke oppgavestyrer" }
        }
    }) {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(
                    oppgaveKoTjeneste.hentKøerForSaksbehandler(
                        område = coroutineContext.område(),
                        kode6 = pepClient.harTilgangTilKode6(),
                        saksbehandlerId = call.queryParameters["saksbehandlerId"]?.toLong()!!,
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
