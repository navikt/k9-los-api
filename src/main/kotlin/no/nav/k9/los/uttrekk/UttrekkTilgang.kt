package no.nav.k9.los.uttrekk

import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.RoutingContext
import no.nav.k9.los.infrastruktur.brukerkontekst.BrukerkontekstMedOmråde
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.getKoin

internal suspend fun RoutingContext.medUttrekkTilgang(block: suspend (BrukerkontekstMedOmråde) -> Unit) {
    medBrukerkontekst { bruker ->
        if (!bruker.harBasisTilgang) {
            call.respond(HttpStatusCode.Forbidden)
            return@medBrukerkontekst
        }
        val saksbehandler = call.application.getKoin().get<SaksbehandlerRepository>()
            .finnSaksbehandlerMedIdent(bruker.navIdent, bruker.harTilgangTilKode6)
        if (saksbehandler == null) {
            call.respond(HttpStatusCode.Forbidden)
            return@medBrukerkontekst
        }
        val tjeneste = call.application.getKoin().get<UttrekkTjeneste>()
        try {
            // Alle direkte ID-ruter, inkludert nedlasting, passerer samme eier- og områdekontroll.
            call.parameters["id"]?.let { verdi ->
                val id = verdi.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@medBrukerkontekst
                }
                tjeneste.krevTilgang(id, saksbehandler.id, bruker.område, bruker.harTilgangTilKode6)
            }
            call.parameters["lagretSokId"]?.let { verdi ->
                val id = verdi.toLongOrNull()
                if (id == null) {
                    call.respond(HttpStatusCode.BadRequest)
                    return@medBrukerkontekst
                }
                tjeneste.krevTilgangTilLagretSøk(id, saksbehandler.id, bruker.område)
            }
            block(bruker)
        } catch (_: SecurityException) {
            call.respond(HttpStatusCode.Forbidden)
        }
    }
}
