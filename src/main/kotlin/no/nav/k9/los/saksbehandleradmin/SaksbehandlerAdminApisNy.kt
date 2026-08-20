package no.nav.k9.los.saksbehandleradmin

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.kontekst.medBrukerkontekst
import no.nav.k9.los.reservasjon.ReservasjonApisTjeneste
import org.koin.ktor.ext.inject

internal fun Route.SaksbehandlerAdminApisNy() {
    val saksbehandlerAdminTjeneste by inject<SaksbehandlerAdminTjeneste>()
    val pepClient by inject<IPepClient>()

    // TODO: slett når frontend har begynt å bruke nytt endepunkt i ReservasjonApis
    val reservasjonApisTjeneste by inject<ReservasjonApisTjeneste>()

    get("/saksbehandlere") {
        medBrukerkontekst { kontekst ->
            if (with(kontekst) { pepClient.erOppgaveStyrer() }) {
                call.respond(saksbehandlerAdminTjeneste.hentSaksbehandlere(kontekst.område, with(kontekst) { pepClient.harTilgangTilKode6() }))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    // TODO: slett når frontend har begynt å bruke nytt endepunkt
    post("/saksbehandlere/sok") {
        medBrukerkontekst { kontekst ->
            if (with(kontekst) { pepClient.erOppgaveStyrer() }) {
                val epost = call.receive<EpostDto>()
                call.respond(
                    saksbehandlerAdminTjeneste.søkSaksbehandler(
                        epost,
                        kontekst.område,
                        with(kontekst) { pepClient.harTilgangTilKode6() },
                    )
                )
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/legg-til") {
        medBrukerkontekst { kontekst ->
            if (with(kontekst) { pepClient.erOppgaveStyrer() }) {
                val request = call.receive<EpostDto>()
                call.respond(saksbehandlerAdminTjeneste.leggTilSaksbehandlerForEpost(request.epost, kontekst.område))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/slett") {
        medBrukerkontekst { kontekst ->
            if (with(kontekst) { pepClient.erOppgaveStyrer() }) {
                val request = call.receive<EpostDto>()
                call.respond(with(kontekst) { saksbehandlerAdminTjeneste.slettSaksbehandler(request.epost, kontekst.område) })
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    post("/saksbehandlere/slettForId") {
        medBrukerkontekst { kontekst ->
            if (with(kontekst) { pepClient.erOppgaveStyrer() }) {
                val id = call.receive<Long>()
                call.respond(with(kontekst) { saksbehandlerAdminTjeneste.slettSaksbehandlerForId(id) })
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }

    // TODO: slett når frontend har begynt å bruke nytt endepunkt i ReservasjonApis
    get("reservasjoner") {
        medBrukerkontekst { kontekst ->
            if (with(kontekst) { pepClient.erOppgaveStyrer() }) {
                call.respond(reservasjonApisTjeneste.hentAlleAktiveReservasjoner(kontekst))
            } else {
                call.respond(HttpStatusCode.Forbidden)
            }
        }
    }
}
