package no.nav.k9.los.domeneadaptere.k9.eventmottak.punsj

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.k9.los.domeneadaptere.k9.eventmottak.EventHandlerMetrics
import no.nav.k9.los.domeneadaptere.k9.eventmottak.FeilRekkefølgeSjekker
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.kodeverk.BehandlingType
import no.nav.k9.los.kodeverk.Fagsystem
import org.jetbrains.annotations.VisibleForTesting
import org.slf4j.LoggerFactory


class K9PunsjEventHandler (
    private val transactionalManager: TransactionalManager,
    private val oppgaveAdapter: EventTilOppgaveAdapter,
    private val eventRepository: EventRepository,
    private val feilRekkefølgeSjekker: FeilRekkefølgeSjekker,
) {
    private val log = LoggerFactory.getLogger(K9PunsjEventHandler::class.java)

    companion object {
        private val typer = BehandlingType.values().filter { it.kodeverk == "PUNSJ_INNSENDING_TYPE" }
    }

    @VisibleForTesting
    fun prosesser(event: K9PunsjEventDto) {
        prosesser(
            eksternId = event.eksternId.toString(),
            eksternVersjon = event.eventTid.toString(),
            event = LosObjectMapper.instance.writeValueAsString(event)
        )
    }

    @WithSpan
    fun prosesser(eksternId: String, eksternVersjon: String, event: String) {
        EventHandlerMetrics.time("k9punsj", "gjennomført") {
            transactionalManager.transaction { tx ->
                val eventnøkkel = eventRepository.lagre(Fagsystem.PUNSJ, eksternId, eksternVersjon, event, tx)
                val alleEventer = eventRepository.hentAlleEventerMedLås(eventnøkkel, tx)

                if (feilRekkefølgeSjekker.sjekkFeilRekkefølge(alleEventer)) {
                    log.warn(
                        "Oppgave med fagsystem: ${eventnøkkel.fagsystem}, eksternId: ${eventnøkkel.eksternId} " +
                            "har fått meldinger i feil rekkefølge. Bestiller historikkvask."
                    )
                    eventRepository.bestillHistorikkvask(eventnøkkel.fagsystem, eventnøkkel.eksternId, tx)
                } else {
                    OpentelemetrySpanUtil.span("punsjTilLosAdapterTjeneste.oppdaterOppgaveForEksternId") {
                        try {
                            oppgaveAdapter.oppdaterOppgaveForEksternId(eventnøkkel, tx, eventer = alleEventer)
                        } catch (e: Exception) {
                            log.error("Oppatering av k9-punsj-oppgave feilet for ${eksternId}. Oppgaven er ikke oppdatert, men blir plukket av vaktmester", e)
                        }
                    }
                }
            }
        }
    }
}
