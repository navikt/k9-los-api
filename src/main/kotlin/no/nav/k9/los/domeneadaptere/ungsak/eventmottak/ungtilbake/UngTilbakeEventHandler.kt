package no.nav.k9.los.domeneadaptere.ungsak.eventmottak.ungtilbake

import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.k9.eventmottak.FeilRekkefølgeSjekker
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.kodeverk.Fagsystem
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.slf4j.LoggerFactory

class UngTilbakeEventHandler(
    private val eventRepository: EventRepository,
    private val transactionalManager: TransactionalManager,
    private val feilRekkefølgeSjekker: FeilRekkefølgeSjekker
) {
    private val log = LoggerFactory.getLogger(UngTilbakeEventHandler::class.java)

    fun prosesser(eksternId: String, eksternVersjon: String, event: String) {
        transactionalManager.transaction { tx ->
            eventRepository.lagre(Fagsystem.UNGTILBAKE, eksternId, eksternVersjon, event, Områder.AKTIVITETSPENGER, tx)
            // TODO: koble på EventTilOppgaveAdapter når ung-tilbake oppgavehåndtering er implementert
        }
    }
}