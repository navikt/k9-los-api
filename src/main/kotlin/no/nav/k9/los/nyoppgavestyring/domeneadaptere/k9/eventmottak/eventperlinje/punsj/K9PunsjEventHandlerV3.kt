package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventperlinje.punsj

import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import org.slf4j.LoggerFactory

class K9PunsjEventHandlerV3(
    private val eventRepository: EventRepository,
) {
    private val log = LoggerFactory.getLogger(K9PunsjEventHandlerV3::class.java)

    companion object {
        private val typer = BehandlingType.values().filter { it.kodeverk == "PUNSJ_INNSENDING_TYPE" }
    }

    fun prosesser(event: PunsjEventDto) {
        log.debug(event.safePrint())
        eventRepository.lagre(event = event)


    }
}
