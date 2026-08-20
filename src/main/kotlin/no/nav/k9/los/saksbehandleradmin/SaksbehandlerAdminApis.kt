package no.nav.k9.los.saksbehandleradmin

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.kontekst.medBrukerkontekst
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.reservasjon.ReservasjonApisTjeneste
import org.koin.ktor.ext.inject

internal fun Route.SaksbehandlerAdminApis() {
    val saksbehandlerAdminTjeneste by inject<SaksbehandlerAdminTjeneste>()
    val pepClient by inject<IPepClient>()

    // TODO: slett når frontend har begynt å bruke nytt endepunkt i ReservasjonApis
    val reservasjonApisTjeneste by inject<ReservasjonApisTjeneste>()

    get("/saksbehandlere") {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                call.respond(saksbehandlerAdminTjeneste.hentSaksbehandlere(kontekst.område, pepClient.harTilgangTilKode6(kontekst)))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    // TODO: slett når frontend har begynt å bruke nytt endepunkt
    post("/saksbehandlere/sok") {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                val epost = call.receive<EpostDto>()
                call.respond(
                    saksbehandlerAdminTjeneste.søkSaksbehandler(
                        epost,
                        Områder.K9,
                        pepClient.harTilgangTilKode6(kontekst),
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/legg-til") {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                val request = call.receive<EpostDto>()
                call.respond(saksbehandlerAdminTjeneste.leggTilSaksbehandlerForEpost(request.epost, Områder.K9))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/slett") {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                val request = call.receive<EpostDto>()
                call.respond(saksbehandlerAdminTjeneste.slettSaksbehandler(request.epost, Områder.K9, kontekst))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/slettForId") {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                val id = call.receive<Long>()
                call.respond(saksbehandlerAdminTjeneste.slettSaksbehandlerForId(id, kontekst))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    // TODO: slett når frontend har begynt å bruke nytt endepunkt i ReservasjonApis
    get("reservasjoner") {
        medBrukerkontekst { kontekst ->
            if (pepClient.erOppgaveStyrer(kontekst)) {
                call.respond(reservasjonApisTjeneste.hentAlleAktiveReservasjoner(kontekst))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
