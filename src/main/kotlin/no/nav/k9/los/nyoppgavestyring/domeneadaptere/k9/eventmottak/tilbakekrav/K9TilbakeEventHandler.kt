package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav

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


class K9TilbakeEventHandler(
    private val eventRepository: EventRepository,
    private val oppgaveAdapter: EventTilOppgaveAdapter,
    private val transactionalManager: TransactionalManager,
) {

    companion object {
        private val log = LoggerFactory.getLogger(K9TilbakeEventHandler::class.java)
    }

    @VisibleForTesting
    fun prosesser(
        event: K9TilbakeEventDto
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
        EventHandlerMetrics.time("k9tilbake", "gjennomført") {
            transactionalManager.transaction { tx ->
                val lås = eventRepository.upsertOgLåsEventnøkkel(Fagsystem.K9TILBAKE, eksternId, tx)
                eventRepository.lagre(Fagsystem.K9TILBAKE, eksternId, eksternVersjon, event, tx)
            }

            OpentelemetrySpanUtil.span("k9TilbakeTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid") {
                try {
                    oppgaveAdapter.oppdaterOppgaveForEksternId(EventNøkkel(Fagsystem.K9TILBAKE, eksternId))
                } catch (e: Exception) {
                    log.error("Oppatering av k9-tilbake-oppgave feilet for ${eksternId}. Oppgaven er ikke oppdatert, men blir plukket av vaktmester", e)
                }
            }
        }
    }
}