package no.nav.k9.los.innloggetbruker

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.abac.IPepClient
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.infrastruktur.rest.område
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject
import org.slf4j.LoggerFactory

internal fun Route.InnloggetBrukerApi() {
    val requestContextService by inject<RequestContextService>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()
    val innloggetBrukerTjeneste by inject<InnloggetBrukerTjeneste>()
    val pepClient by inject<IPepClient>()

    val log = LoggerFactory.getLogger("InnloggetBrukerApi")

    get("/saksbehandler") {
        requestContextService.withRequestContext(call) {
            val token = coroutineContext.idToken()
            val saksbehandlerIdent = coroutineContext.idToken().getNavIdent()
            val tilganger = pepClient.tilganger(coroutineContext.område())
            val saksbehandler =
                saksbehandlerRepository.finnSaksbehandlerMedIdent(token.getNavIdent(), tilganger.kode6)
                    ?: saksbehandlerRepository.finnSaksbehandlerMedEpost(token.getUsername(), tilganger.kode6)
            if (saksbehandler == null) {
                log.warn("Innlogget bruker finnes ikke i saksbehandlertabell, og kan derfor ikke oppdateres")
            }
            val finnesISaksbehandlerTabell = saksbehandler != null

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
                    epost = token.getUsername(),
                    skjermet = tilganger.kode6
                )
            }
            call.respond(
                innloggetBrukerDto
            )
        }
    }
}
