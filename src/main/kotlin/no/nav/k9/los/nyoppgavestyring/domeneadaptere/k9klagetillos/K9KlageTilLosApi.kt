package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9klagetillos

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import org.koin.ktor.ext.inject

internal fun Route.K9KlageTilLosApi() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveV3Tjeneste by inject<OppgaveV3Tjeneste>()
    val k9KlageTilLosAdapterTjeneste by inject<K9KlageTilLosAdapterTjeneste>()

    delete("/slettOppgavedata") {
        requestContextService.withRequestContext(call) {
            oppgaveV3Tjeneste.slettOppgaveData()
            call.respond("OK")
        }
    }

    put("/startOppgaveprosessering") {
        requestContextService.withRequestContext(call) {
            k9KlageTilLosAdapterTjeneste.kjør(kjørUmiddelbart = true)
            call.respond("OK")
        }
    }
}