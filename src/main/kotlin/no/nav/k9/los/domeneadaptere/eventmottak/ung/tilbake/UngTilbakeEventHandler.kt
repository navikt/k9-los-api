package no.nav.k9.los.domeneadaptere.eventmottak.ung.tilbake

import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.eventlager.Fagsystem
import no.nav.k9.los.domeneadaptere.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.db.medSavepoint
import org.slf4j.LoggerFactory

class UngTilbakeEventHandler(
    private val eventRepository: EventRepository,
    private val transactionalManager: TransactionalManager,
    private val eventTilOppgaveAdapter: EventTilOppgaveAdapter,
) {
    private val log = LoggerFactory.getLogger(UngTilbakeEventHandler::class.java)

    fun prosesser(eksternId: String, eksternVersjon: String, event: String) {
        transactionalManager.transaction { tx ->
            val eventnøkkel = eventRepository.lagreOgSjekkAktiv(Fagsystem.UNGTILBAKE, eksternId, eksternVersjon, event, tx)
                ?: return@transaction
            try {
                tx.medSavepoint { eventTilOppgaveAdapter.oppdaterOppgaveForEksternId(eventnøkkel, tx) }
            } catch (e: Exception) {
                log.error("Oppdatering av ung-tilbake-oppgave feilet for $eksternId. Eventet blir plukket opp av vaktmester", e)
            }
        }
    }
}