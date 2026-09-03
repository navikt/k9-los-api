package no.nav.k9.los.innloggetbruker

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.koin.ktor.ext.inject

// TODO: Slett når frontend bruker det områdeparameteriserte endepunktet.
internal fun Route.LegacyInnloggetBrukerApi() {
    val innloggetBrukerTjeneste by inject<InnloggetBrukerTjeneste>()
    val configuration by inject<Configuration>()

    get("/saksbehandler") {
        if (configuration.koinProfile() != KoinProfile.LOCAL) {
            medBrukerkontekst { bruker ->
                call.respond(innloggetBrukerTjeneste.hentLegacyInnloggetBruker(bruker))
            }
        } else {
            call.respond(lokalLegacyInnloggetBruker())
        }
    }
}

internal fun lokalLegacyInnloggetBruker() = LegacyInnloggetBrukerDto(
    brukernavn = "saksbehandler@nav.no",
    navn = "Saksbehandler Sara",
    brukerIdent = "Z123456",
    id = 1,
    kanSaksbehandle = true,
    kanOppgavestyre = true,
    kanReservere = true,
    kanDrifte = true,
    finnesISaksbehandlerTabell = true,
    områder = listOf(Områder.K9),
)
