package no.nav.k9.los.saksbehandleradmin

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.reservasjon.ReservasjonApisTjeneste
import org.koin.ktor.ext.inject

internal fun Route.SaksbehandlerAdminApisNy() {
    val saksbehandlerAdminTjeneste by inject<SaksbehandlerAdminTjeneste>()

    // TODO: slett når frontend har begynt å bruke nytt endepunkt i ReservasjonApis
    val reservasjonApisTjeneste by inject<ReservasjonApisTjeneste>()

    get("/saksbehandlere") {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer()) {
                call.respond(saksbehandlerAdminTjeneste.hentSaksbehandlere(bruker.område, bruker.harTilgangTilKode6()))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    // TODO: slett når frontend har begynt å bruke nytt endepunkt
    post("/saksbehandlere/sok") {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer()) {
                val epost = call.receive<EpostDto>()
                call.respond(
                    saksbehandlerAdminTjeneste.søkSaksbehandler(
                        epost,
                        bruker.område,
                        bruker.harTilgangTilKode6(),
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/legg-til") {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer()) {
                val request = call.receive<EpostDto>()
                call.respond(saksbehandlerAdminTjeneste.leggTilSaksbehandlerForEpost(request.epost, bruker.område))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/slett") {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer()) {
                val request = call.receive<EpostDto>()
                call.respond(saksbehandlerAdminTjeneste.slettSaksbehandler(request.epost, bruker.område, bruker))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/slettForId") {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer()) {
                val id = call.receive<Long>()
                call.respond(saksbehandlerAdminTjeneste.slettSaksbehandlerForId(id, bruker))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    // TODO: slett når frontend har begynt å bruke nytt endepunkt i ReservasjonApis
    get("reservasjoner") {
        medBrukerkontekst { bruker ->
            if (bruker.erOppgavestyrer()) {
                call.respond(reservasjonApisTjeneste.hentAlleAktiveReservasjoner(bruker))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
