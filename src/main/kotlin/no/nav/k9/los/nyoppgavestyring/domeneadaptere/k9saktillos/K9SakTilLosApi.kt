package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos

import io.ktor.application.*
import io.ktor.response.*
import io.ktor.routing.*
import no.nav.k9.los.integrasjon.rest.RequestContextService
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3Tjeneste
import org.koin.ktor.ext.inject

internal fun Route.K9SakTilLosApi() {
    val requestContextService by inject<RequestContextService>()
    val oppgaveV3Tjeneste by inject<OppgaveV3Tjeneste>()
    val k9SakTilLosAdapterTjeneste by inject<K9SakTilLosAdapterTjeneste>()

    delete("/slettOppgavedata") {
        requestContextService.withRequestContext(call) {
            oppgaveV3Tjeneste.slettOppgaveData()
            call.respond("OK")
        }
    }

    put("/startOppgaveprosessering") {
        requestContextService.withRequestContext(call) {
            k9SakTilLosAdapterTjeneste.kjør(kjørUmiddelbart = true)
            call.respond("OK")
        }
    }
}