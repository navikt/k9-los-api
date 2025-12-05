package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHandlerMetrics
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventNøkkel
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.EventTilOppgaveAdapter
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import org.slf4j.LoggerFactory


class K9PunsjEventHandler (
    private val transactionalManager: TransactionalManager,
    private val oppgaveAdapter: EventTilOppgaveAdapter,
    private val eventRepository: EventRepository,
) {
    private val log = LoggerFactory.getLogger(K9PunsjEventHandler::class.java)

    companion object {
        private val typer = BehandlingType.values().filter { it.kodeverk == "PUNSJ_INNSENDING_TYPE" }
    }

    @WithSpan
    fun prosesser(event: K9PunsjEventDto) {
        EventHandlerMetrics.time("k9punsj", "gjennomført") {
            log.info(event.safePrint())

            transactionalManager.transaction { tx ->
                val lås =
                    eventRepository.upsertOgLåsEventnøkkel(Fagsystem.PUNSJ, event.eksternId.toString(), tx)

                log.info(event.safePrint())
                eventRepository.lagre(Fagsystem.PUNSJ, LosObjectMapper.instance.writeValueAsString(event), tx)
            }

            OpentelemetrySpanUtil.span("punsjTilLosAdapterTjeneste.oppdaterOppgaveForEksternId") {
                try {
                    oppgaveAdapter.oppdaterOppgaveForEksternId(
                        EventNøkkel(
                            null,
                            Fagsystem.PUNSJ,
                            event.eksternId.toString()
                        )
                    )
                } catch (e: Exception) {
                    log.error("Oppatering av k9-punsj-oppgave feilet for ${event.eksternId}. Oppgaven er ikke oppdatert, men blir plukket av vaktmester", e)
                }
            }
        }
    }
}
