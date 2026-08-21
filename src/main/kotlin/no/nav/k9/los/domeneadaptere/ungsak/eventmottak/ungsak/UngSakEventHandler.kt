package no.nav.k9.los.domeneadaptere.ungsak.eventmottak.ungsak

import no.nav.k9.los.domeneadaptere.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.k9.eventmottak.FeilRekkefølgeSjekker
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.kodeverk.Fagsystem
import org.slf4j.LoggerFactory

class UngSakEventHandler (
    private val eventRepository: EventRepository,
    //private val eventTilOppgaveAdapter: SakEventTilOppgaveAdapter,
    private val transactionalManager: TransactionalManager,
    private val feilRekkefølgeSjekker: FeilRekkefølgeSjekker
) {
    private val log = LoggerFactory.getLogger(UngSakEventHandler::class.java)
    fun prosesser(eksternId: String, eksternVersjon: String, event: String) {
        transactionalManager.transaction { tx ->
            val eventnøkkel = eventRepository.lagre(Fagsystem.UNGSAK, eksternId, eksternVersjon, event, tx)
            /*
            val alleEventer = eventRepository.hentAlleEventerMedLås(eventnøkkel, tx)
            if (feilRekkefølgeSjekker.sjekkFeilRekkefølge(alleEventer)) {
                log.warn(
                    "Oppgave med fagsystem: ${eventnøkkel.fagsystem}, eksternId: ${eventnøkkel.eksternId} " +
                            "har fått meldinger i feil rekkefølge. Bestiller historikkvask."
                )
                eventRepository.bestillHistorikkvask(eventnøkkel.fagsystem, eventnøkkel.eksternId, tx)
            }

 */
        }
    }
}