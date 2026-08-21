package no.nav.k9.los.innloggetbruker

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.brukerkontekst.medBrukerkontekst
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

internal fun Route.InnloggetBrukerApi() {
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val azureGraphService by inject<IAzureGraphService>()
    val configuration by inject<Configuration>()

    val log = LoggerFactory.getLogger("InnloggetBrukerApi")

    get("/saksbehandler") {
        if (configuration.koinProfile() != KoinProfile.LOCAL) {
            medBrukerkontekst { bruker ->
                val token = bruker.idToken
                val skjermet = bruker.harTilgangTilKode6
                log.info("Henter innlogget saksbehandler med epost ${token.getUsername()} og navn ${token.getName()}")
                val saksbehandlerIdent = bruker.navIdent
                val saksbehandler =
                    saksbehandlerRepository.finnSaksbehandlerMedIdent(token.getNavIdent(), skjermet)
                        ?: saksbehandlerRepository.finnSaksbehandlerMedEpost(token.getUsername(), skjermet)
                if (saksbehandler == null) {
                    log.warn("Saksbehandler med epost ${token.getUsername()} finnes ikke i saksbehandlertabell, og kan derfor ikke oppdateres")
                }
                val finnesISaksbehandlerTabell = saksbehandler != null

                val innloggetBrukerDto = InnloggetBrukerDto(
                    token.getUsername(),
                    token.getName(),
                    brukerIdent = saksbehandlerIdent,
                    id = saksbehandler?.let { saksbehandler.id },
                    kanSaksbehandle = bruker.harBasisTilgang, //TODO mismatch mellom navnet 'kanSaksbehandle' og at alle som har tilgang til systemet har basistilgang
                    kanOppgavestyre = bruker.erOppgavestyrer,
                    kanReservere = bruker.harTilgangTilReserveringAvOppgaver,
                    kanDrifte = bruker.kanLeggeUtDriftsmelding,
                    finnesISaksbehandlerTabell = finnesISaksbehandlerTabell,
                    områder = saksbehandler?.områder ?: emptyList()
                )
                if (!innloggetBrukerDto.kanSaksbehandle) {
                    log.warn("Saksbehandler med epost ${token.getUsername()} har ikke basistilgang, og kan derfor ikke bruke systemet")
                }
                if (finnesISaksbehandlerTabell) {
                    //  vedlikeholder saksbehandler-feltene etter at epost er lagt inn av avdelingsleder
                    saksbehandlerRepository.vedlikeholdSaksbehandler(
                        Saksbehandler(
                            id = null,
                            navident = saksbehandlerIdent,
                            navn = token.getName(),
                            epost = token.getUsername(),
                            enhet = azureGraphService.hentEnhet(bruker),
                            områder = saksbehandler.områder
                        ),
                        skjermet
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
                    finnesISaksbehandlerTabell = true,
                    områder = listOf(Områder.K9)
                )
            )
        }
    }
}
