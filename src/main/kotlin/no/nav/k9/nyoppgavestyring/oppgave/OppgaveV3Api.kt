package no.nav.k9.nyoppgavestyring.oppgave

import io.ktor.application.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.k9.integrasjon.rest.RequestContextService
import no.nav.k9.nyoppgavestyring.adaptere.k9saktillosadapter.K9SakTilLosAdapterTjeneste
import org.koin.ktor.ext.inject

internal fun Route.OppgaveV3Api() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveV3Tjeneste by inject<OppgaveV3Tjeneste>()
    val k9SakTilLosAdapterTjeneste by inject<K9SakTilLosAdapterTjeneste>()

    put {
        requestContextService.withRequestContext(call) {
            val oppgaveDto = call.receive<OppgaveDto>()

            oppgaveV3Tjeneste.sjekkDuplikatOgProsesser(oppgaveDto)

            call.respond("OK")
        }
    }

    put("/startOppgaveprosessering") {
        requestContextService.withRequestContext(call) {
            val kjørSetup = call.request.queryParameters["kjorSetup"].toBoolean()
            k9SakTilLosAdapterTjeneste.kjør(kjørSetup)
        }
    }

}