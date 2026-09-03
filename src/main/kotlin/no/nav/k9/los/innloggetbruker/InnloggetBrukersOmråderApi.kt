package no.nav.k9.los.innloggetbruker

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekstUtenOmråde
import org.koin.ktor.ext.inject

internal fun Route.InnloggetBrukersOmråderApi() {
    val innloggetBrukerTjeneste by inject<InnloggetBrukerTjeneste>()

    get {
        medBrukerkontekstUtenOmråde { bruker ->
            call.respond(innloggetBrukerTjeneste.hentBrukersOmråder(bruker))
        }
    }
}
