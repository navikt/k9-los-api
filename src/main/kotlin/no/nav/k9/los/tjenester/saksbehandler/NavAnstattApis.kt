package no.nav.k9.los.tjenester.saksbehandler

import io.ktor.server.application.*
import io.ktor.server.locations.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.integrasjon.rest.RequestContextService
import org.koin.ktor.ext.inject

internal fun Route.NavAnsattApis() {
    val pepClient by inject<IPepClient>()
    val requestContextService by inject<RequestContextService>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val azureGraphService by inject<IAzureGraphService>()
    val configuration by inject<Configuration>()

    @Location("/saksbehandler")
    class getInnloggetBruker
    get { _: getInnloggetBruker ->
        if (configuration.koinProfile() != KoinProfile.LOCAL) {
            requestContextService.withRequestContext(call) {
                val token = call.idToken()
                val saksbehandlerIdent = azureGraphService.hentIdentTilInnloggetBruker()
                val innloggetNavAnsattDto = InnloggetNavAnsattDto(
                    token.getUsername(),
                    token.getName(),
                    brukerIdent = saksbehandlerIdent,
                    kanSaksbehandle = pepClient.harBasisTilgang(),
                    kanVeilede = pepClient.harBasisTilgang(),
                    kanBeslutte = pepClient.harBasisTilgang(),
                    kanBehandleKodeEgenAnsatt = pepClient.harBasisTilgang(),
                    kanBehandleKode6 = pepClient.harBasisTilgang(),
                    kanBehandleKode7 = pepClient.harBasisTilgang(),
                    kanOppgavestyre = pepClient.erOppgaveStyrer(),
                    kanReservere = pepClient.harTilgangTilReservingAvOppgaver(),
                    kanDrifte = pepClient.kanLeggeUtDriftsmelding()
                )
                if (saksbehandlerRepository.finnSaksbehandlerMedEpost(token.getUsername()) != null) {
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
                    innloggetNavAnsattDto
                )
            }
        } else {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    brukerIdent = "Z123456",
                    navn = "Saksbehandler Sara",
                    epost = "saksbehandler@nav.no",
                    reservasjoner = mutableSetOf(),
                    enhet = "NAV DRIFT"
                )
            )
            call.respond(
                InnloggetNavAnsattDto(
                    "saksbehandler@nav.no",
                    "Saksbehandler Sara",
                    "Z123456",
                    kanSaksbehandle = true,
                    kanVeilede = true,
                    kanBeslutte = true,
                    kanBehandleKodeEgenAnsatt = true,
                    kanBehandleKode6 = true,
                    kanBehandleKode7 = true,
                    kanOppgavestyre = true,
                    kanReservere = true,
                    kanDrifte = true
                )
            )
        }
    }
}
