package no.nav.k9.los.innloggetbruker

import io.github.smiley4.ktoropenapi.get
import io.ktor.http.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import org.koin.ktor.ext.inject

internal fun Route.InnloggetBrukerApi() {
    val innloggetBrukerTjeneste by inject<InnloggetBrukerTjeneste>()

    get({
        description = "Henter innlogget bruker og tilganger for valgt område."
        response {
            HttpStatusCode.OK to { body<InnloggetBrukerDto>() }
        }
    }) {
        medBrukerkontekst { bruker ->
            call.respond(innloggetBrukerTjeneste.hentInnloggetBruker(bruker))
        }
    }
}
