package no.nav.k9.los.domeneadaptere.eventmottak.ung.sak

import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventmottak.FeilRekkefølgeSjekker
import no.nav.k9.los.domeneadaptere.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.db.medSavepoint
import org.slf4j.LoggerFactory

class UngSakEventHandler (
    private val eventRepository: EventRepository,
    private val eventTilOppgaveAdapter: EventTilOppgaveAdapter,
    private val transactionalManager: TransactionalManager,
) {
    private val log = LoggerFactory.getLogger(UngSakEventHandler::class.java)
    fun prosesser(eksternId: String, eksternVersjon: String, event: String) {
        transactionalManager.transaction { tx ->
            val eventnøkkel = eventRepository.lagre(Fagsystem.UNGSAK, eksternId, eksternVersjon, event, tx)

            try {
                tx.medSavepoint {
                    eventTilOppgaveAdapter.oppdaterOppgaveForEksternId(eventnøkkel, tx)
                }
            } catch (e: Exception) {
                log.error(
                    "Oppatering av ung-sak-oppgave feilet for ${eksternId}. Oppgaven er ikke oppdatert, men blir plukket av vaktmester",
                    e
                )
            }
        }
    }
}