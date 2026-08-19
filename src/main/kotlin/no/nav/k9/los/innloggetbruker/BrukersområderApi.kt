package no.nav.k9.los.innloggetbruker

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.kontekst.medInnloggetBruker
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

internal fun Route.BrukersområderApi() {
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()

    get {
        medInnloggetBruker { bruker ->
            val områder: List<Områder> = (
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(bruker.navIdent)
                        ?: saksbehandlerRepository.finnSaksbehandlerMedEpost(bruker.idToken.getUsername())
                    )
                ?.områder
                ?.sortedBy { it.eksternId }
                ?: emptyList()

            call.respond(områder)
        }
    }
}
