package no.nav.k9.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHandlerMetrics.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHandlerMetrics
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.K9PunsjEventRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import org.slf4j.LoggerFactory


class K9PunsjEventHandler (
    private val punsjEventK9Repository: K9PunsjEventRepository,
    private val punsjTilLosAdapterTjeneste: K9PunsjTilLosAdapterTjeneste,
) {
    private val log = LoggerFactory.getLogger(K9PunsjEventHandler::class.java)

    companion object {
        private val typer = BehandlingType.values().filter { it.kodeverk == "PUNSJ_INNSENDING_TYPE" }
    }

    @WithSpan
    fun prosesser(event: PunsjEventDto) {
        EventHandlerMetrics.time("k9punsj", "gjennomført") {
            log.info(event.safePrint())

            punsjEventK9Repository.lagre(event = event)

            OpentelemetrySpanUtil.span("punsjTilLosAdapterTjeneste.oppdaterOppgaveForEksternId") {
                try {
                    punsjTilLosAdapterTjeneste.oppdaterOppgaveForEksternId(
                        event.eksternId
                    )
                } catch (e: Exception) {
                    log.error("Oppatering av k9-punsj-oppgave feilet for ${event.eksternId}. Oppgaven er ikke oppdatert, men blir plukket av vaktmester", e)
                }
            }
        }
    }
}
