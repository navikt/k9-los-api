package no.nav.k9.los.innloggetbruker

import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.infrastruktur.rest.RequestContextService
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.koin.ktor.ext.inject

internal fun Route.BrukersområderApi() {
    val requestContextService by inject<RequestContextService>()
    val saksbehandlerRepository by inject<SaksbehandlerRepository>()

    BrukersområderApi(
        requestContextService = requestContextService,
        saksbehandlerRepository = saksbehandlerRepository,
    )
}

internal fun Route.BrukersområderApi(
    requestContextService: RequestContextService,
    saksbehandlerRepository: SaksbehandlerRepository,
) {
    get {
        requestContextService.withRequestContext(call) {
            val token = coroutineContext.idToken()
            val områder = (
                saksbehandlerRepository.finnSaksbehandlerMedIdent(token.getNavIdent())
                    ?: saksbehandlerRepository.finnSaksbehandlerMedEpost(token.getUsername())
                )
                ?.områder
                ?.sortedBy { it.eksternId }
                ?: emptyList()

            call.respond(områder)
        }
    }
}


