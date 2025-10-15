package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager

import io.github.smiley4.ktoropenapi.put
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

internal fun Route.EventlagerApi() {
    val eventlagerKonverteringsjobb by inject<EventlagerKonverteringsjobb>()

    put("/startEventlagerKonvertering", {
        tags("Forvaltning")
    }) {
        eventlagerKonverteringsjobb.kjørEventlagerKonvertering()
    }
}