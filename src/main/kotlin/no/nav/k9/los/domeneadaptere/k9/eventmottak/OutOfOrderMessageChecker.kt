package no.nav.k9.los.domeneadaptere.k9.eventmottak

import kotliquery.TransactionalSession
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import org.slf4j.LoggerFactory

/**
 * Checks if incoming events are out of order by comparing dirty and non-dirty events.
 * Out of order means: there exists a dirty event that is newer (per ekstern_versjon)
 * than an existing non-dirty event.
 */
class OutOfOrderMessageChecker(
    private val eventRepository: EventRepository,
) {
    private val log = LoggerFactory.getLogger(OutOfOrderMessageChecker::class.java)

    /**
     * Checks if there are dirty events that are newer than existing non-dirty events.
     * This indicates messages have arrived out of order.
     *
     * @return true if out of order is detected, false otherwise
     */
    fun checkOutOfOrder(
        eventnøkkel: EventNøkkel,
        tx: TransactionalSession,
    ): Boolean {
        val allEvents = eventRepository.hentAlleEventerMedLås(eventnøkkel, tx)
        if (allEvents.isEmpty()) return false

        var harSettClean = false
        for (event in allEvents.asReversed()) {
            if (!event.dirty) {
                harSettClean = true
            } else if (harSettClean) {
                log.warn(
                    "Oppgave med fagsystem: ${eventnøkkel.fagsystem}, eksternId: ${eventnøkkel.eksternId} " +
                        "har fått meldinger i feil rekkefølge. Bestiller historikkvask!"
                )
                return true
            }
        }

        return false
    }
}
