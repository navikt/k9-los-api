package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottakoghistorikk.punsj

import no.nav.k9.los.domene.modell.BehandlingType
import org.slf4j.LoggerFactory

class K9punsjEventHandlerV3(
    private val eventRepository: EventRepository,
) {
    private val log = LoggerFactory.getLogger(K9punsjEventHandlerV3::class.java)

    companion object {
        private val typer = BehandlingType.values().filter { it.kodeverk == "PUNSJ_INNSENDING_TYPE" }
    }

    fun prosesser(event: PunsjEventV3Dto) {
        log.debug(event.safePrint())
        eventRepository.lagre(event = event)

    }
}
