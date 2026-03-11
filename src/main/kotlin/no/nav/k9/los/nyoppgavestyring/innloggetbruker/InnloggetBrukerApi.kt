package no.nav.k9.los.nyoppgavestyring.innloggetbruker

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.nyoppgavestyring.infrastruktur.idtoken.idToken
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

internal fun Route.InnloggetBrukerApi() {
    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val azureGraphService by inject<IAzureGraphService>()
    val configuration by inject<Configuration>()

    get("/saksbehandler") {
        if (configuration.koinProfile() != KoinProfile.LOCAL) {
            requestContextService.withRequestContext(call) {
                val token = call.idToken()
                val saksbehandlerIdent = azureGraphService.hentIdentTilInnloggetBruker()
                val saksbehandler =
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(token.getNavIdent())
                val finnesISaksbehandlerTabell = saksbehandler != null

                val innloggetBrukerDto = InnloggetBrukerDto(
                    token.getUsername(),
                    token.getName(),
                    brukerIdent = saksbehandlerIdent,
                    id = saksbehandler?.let { saksbehandler.id },
                    kanSaksbehandle = pepClient.harBasisTilgang(), //TODO mismatch mellom navnet 'kanSaksbehandle' og at alle som har tilgang til systemet har basistilgang
                    kanOppgavestyre = pepClient.erOppgaveStyrer(),
                    kanReservere = pepClient.harTilgangTilReserveringAvOppgaver(),
                    kanDrifte = pepClient.kanLeggeUtDriftsmelding(),
                    finnesISaksbehandlerTabell = finnesISaksbehandlerTabell
                )
                if (finnesISaksbehandlerTabell) {
                    //  oppdaterer saksbehandler i tabell etter at epost er lagt inn av avdelingsleder
                    saksbehandlerRepository.addSaksbehandler(
                        Saksbehandler(
                            id = null,
                            brukerIdent = saksbehandlerIdent,
                            navn = token.getName(),
                            epost = token.getUsername(),
                            reservasjoner = mutableSetOf(),
                            enhet = azureGraphService.hentEnhetForInnloggetBruker()
                        )
                    )
                }
                call.respond(
                    innloggetBrukerDto
                )
            }
        } else {
            call.respond(
                InnloggetBrukerDto(
                    "saksbehandler@nav.no",
                    "Saksbehandler Sara",
                    "Z123456",
                    id = 1,
                    kanSaksbehandle = true,
                    kanOppgavestyre = true,
                    kanReservere = true,
                    kanDrifte = true,
                    finnesISaksbehandlerTabell = true
                )
            )
        }
    }
}
