package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHandlerMetrics
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import org.slf4j.LoggerFactory


class K9SakEventHandler(
    private val eventRepository: EventRepository,
    private val eventTilOppgaveAdapter: EventTilOppgaveAdapter,
    private val transactionalManager: TransactionalManager,
) {
    private val log = LoggerFactory.getLogger(K9SakEventHandler::class.java)

    @WithSpan
    fun prosesser(
        eventInn: K9SakEventDto
    ) {
        val t0 = System.nanoTime()

        transactionalManager.transaction { tx ->
            eventRepository.upsertOgLåsEventnøkkel(Fagsystem.K9SAK, eventInn.eksternId.toString(), tx)
            eventRepository.lagre(Fagsystem.K9SAK, LosObjectMapper.instance.writeValueAsString(eventInn), tx)
        }

        OpentelemetrySpanUtil.span("k9SakTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid") {
            try {
                eventTilOppgaveAdapter.oppdaterOppgaveForEksternId(
                    EventNøkkel(
                        null,
                        Fagsystem.K9SAK,
                        eventInn.eksternId.toString()
                    )
                )
            } catch (e: Exception) {
                log.error(
                    "Oppatering av k9-sak-oppgave feilet for ${eventInn.eksternId}. Oppgaven er ikke oppdatert, men blir plukket av vaktmester",
                    e
                )
            }
        }

        EventHandlerMetrics.observe("k9sak", "gjennomført", t0)
    }
}