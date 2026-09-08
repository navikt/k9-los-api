package no.nav.k9.los.innloggetbruker

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.abac.ISifAbacPdpKlient
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.idtoken.idToken
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

internal fun Route.InnloggetBrukerApi() {
    val requestContextService by inject<RequestContextService>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val azureGraphService by inject<IAzureGraphService>()
    val innloggetBrukerTjeneste by inject<InnloggetBrukerTjeneste>()
    val configuration by inject<Configuration>()
    val sifAbacPdpKlient by inject<ISifAbacPdpKlient>()

    val log = LoggerFactory.getLogger("InnloggetBrukerApi")

    get("/saksbehandler") {
        if (configuration.koinProfile() != KoinProfile.LOCAL) {
            requestContextService.withRequestContext(call) {
                val token = call.idToken()
                val saksbehandlerIdent = azureGraphService.hentIdentTilInnloggetBruker()
                val saksbehandler =
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(token.getNavIdent())
                        ?: saksbehandlerRepository.finnSaksbehandlerMedEpost(token.getUsername())
                if (saksbehandler == null) {
                    log.warn("Innlogget bruker finnes ikke i saksbehandlertabell, og kan derfor ikke oppdateres")
                }
                val finnesISaksbehandlerTabell = saksbehandler != null

                val tilganger = sifAbacPdpKlient.hentTilganger(token)

                val innloggetBrukerDto = InnloggetBrukerDto(
                    token.getUsername(),
                    token.getName(),
                    brukerIdent = saksbehandlerIdent,
                    id = saksbehandler?.let { saksbehandler.id },
                    kanSaksbehandle = tilganger.basis, //TODO mismatch mellom navnet 'kanSaksbehandle' og at alle som har tilgang til systemet har basistilgang
                    kanOppgavestyre = tilganger.oppgavestyring,
                    kanReservere = tilganger.reservering,
                    kanDrifte = tilganger.drift,
                    finnesISaksbehandlerTabell = finnesISaksbehandlerTabell
                )
                if (!innloggetBrukerDto.kanSaksbehandle) {
                    log.warn("Innlogget saksbehandler har ikke basistilgang, og kan derfor ikke bruke systemet")
                }
                if (saksbehandler != null) {
                    innloggetBrukerTjeneste.vedlikeholdHvisUtdatert(
                        saksbehandler = saksbehandler,
                        navident = saksbehandlerIdent,
                        navn = token.getName(),
                        epost = token.getUsername()
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
