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
import org.slf4j.LoggerFactory

internal fun Route.InnloggetBrukerApi() {
    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val azureGraphService by inject<IAzureGraphService>()
    val configuration by inject<Configuration>()

    val log = LoggerFactory.getLogger("InnloggetBrukerApi")

    get("/saksbehandler") {
        if (configuration.koinProfile() != KoinProfile.LOCAL) {
            requestContextService.withRequestContext(call) {
                val token = call.idToken()
                log.info("Henter innlogget saksbehandler med epost ${token.getUsername()} og navn ${token.getName()}")
                val saksbehandlerIdent = azureGraphService.hentIdentTilInnloggetBruker()
                val saksbehandler =
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(token.getNavIdent())
                        ?: saksbehandlerRepository.finnSaksbehandlerMedEpost(token.getUsername())
                if (saksbehandler == null) {
                    log.warn("Saksbehandler med epost ${token.getUsername()} finnes ikke i saksbehandlertabell, og kan derfor ikke oppdateres")
                }
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
                if (!innloggetBrukerDto.kanSaksbehandle) {
                    log.warn("Saksbehandler med epost ${token.getUsername()} har ikke basistilgang, og kan derfor ikke bruke systemet")
                }
                if (finnesISaksbehandlerTabell) {
                    //  oppdaterer saksbehandler i tabell etter at epost er lagt inn av avdelingsleder
                    saksbehandlerRepository.addSaksbehandler(
                        Saksbehandler(
                            id = null,
                            navident = saksbehandlerIdent,
                            navn = token.getName(),
                            epost = token.getUsername(),
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
