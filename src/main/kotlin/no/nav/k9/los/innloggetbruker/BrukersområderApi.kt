package no.nav.k9.los.innloggetbruker

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekstUtenOmråde
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

internal fun Route.BrukersområderApi() {
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()

    get {
        medBrukerkontekstUtenOmråde { bruker ->
            val skjermet = bruker.harKode6TilgangIEttEllerFlereOmråder()
            val områder: List<Områder> = (
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(bruker.bruker.navIdent, skjermet)
                        ?: saksbehandlerRepository.finnSaksbehandlerMedEpost(bruker.bruker.idToken.getUsername(), skjermet)
                    )
                ?.områder
                ?.sortedBy { it.eksternId }
                ?: emptyList()

            call.respond(områder)
        }
    }
}
