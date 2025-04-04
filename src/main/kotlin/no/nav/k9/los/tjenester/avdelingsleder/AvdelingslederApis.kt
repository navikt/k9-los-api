package no.nav.k9.los.tjenester.avdelingsleder

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import no.nav.k9.los.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.koin.ktor.ext.inject
import java.util.*

internal fun Route.AvdelingslederApis() {
    val oppgaveTjeneste by inject<OppgaveTjeneste>()
    val avdelingslederTjeneste by inject<AvdelingslederTjeneste>()
    val requestContextService by inject<RequestContextService>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val pepClient by inject<IPepClient>()

    get("/oppgaver/antall-totalt") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(oppgaveTjeneste.hentAntallOppgaverTotalt()) //TODO Må også telle oppgaver som finnes i V3 men ikke V1 (klageoppgaver)?
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    //Gjelder bare gamle køer. For nye, bruk oppgaveKoApis/{id}/antall-oppgaver::GET
    get("/oppgaver/antall") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val uuid = call.parameters["id"]
                call.respond(
                    oppgaveTjeneste.hentAntallOppgaver(
                        oppgavekøId = UUID.fromString(uuid),
                        taMedReserverte = true
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/saksbehandlere") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(avdelingslederTjeneste.hentSaksbehandlere())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    // TODO: slett når frontend har begynt å bruke nytt endepunkt
    post("/saksbehandlere/sok") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val epost = call.receive<EpostDto>()
                call.respond(avdelingslederTjeneste.søkSaksbehandler(epost))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/legg-til") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val epost = call.receive<EpostDto>()
                call.respond(avdelingslederTjeneste.leggTilSaksbehandler(epost.epost))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/slett") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                val epost = call.receive<EpostDto>()
                call.respond(avdelingslederTjeneste.slettSaksbehandler(epost.epost))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    get("/reservasjoner") {
        requestContextService.withRequestContext(call) {
            if (pepClient.erOppgaveStyrer()) {
                call.respond(avdelingslederTjeneste.hentAlleAktiveReservasjonerV3())
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}

