package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHandlerMetrics
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import org.jetbrains.annotations.VisibleForTesting
import org.slf4j.LoggerFactory


class K9KlageEventHandler (
    private val transactionalManager: TransactionalManager,
    private val oppgaveAdapter: EventTilOppgaveAdapter,
    private val eventRepository: EventRepository,
) {
    private val log = LoggerFactory.getLogger(K9KlageEventHandler::class.java)

    @VisibleForTesting
    fun prosesser(
        event: K9KlageEventDto
    ) {
        prosesser(
            eksternId = event.eksternId.toString(),
            eksternVersjon = event.eventTid.toString(),
            event = LosObjectMapper.instance.writeValueAsString(event)
        )
    }

    @WithSpan
    fun prosesser(
        eksternId: String,
        eksternVersjon: String,
        event: String
    ) {
        val t0 = System.nanoTime()

        transactionalManager.transaction { tx ->
            eventRepository.upsertOgLåsEventnøkkel(Fagsystem.K9KLAGE, eksternId, tx)
            eventRepository.lagre(Fagsystem.K9KLAGE, eksternId, eksternVersjon, event, tx)
        }

        OpentelemetrySpanUtil.span("k9KlageTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid") {
            try {
                oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.K9KLAGE, eksternId))
            } catch (e: Exception) {
                log.error("Oppatering av k9-klage-oppgave feilet for ${eksternId}. Oppgaven er ikke oppdatert, men blir plukket av vaktmester", e)
            }
        }

        EventHandlerMetrics.observe("k9klage", "gjennomført", t0)
    }
}
