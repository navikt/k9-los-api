package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj

import io.opentelemetry.instrumentation.annotations.WithSpan
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHandlerMetrics
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventlagerKonverteringsservice
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import org.slf4j.LoggerFactory


class K9PunsjEventHandler (
    private val punsjEventK9Repository: K9PunsjEventRepository,
    private val punsjTilLosAdapterTjeneste: K9PunsjTilLosAdapterTjeneste,
    private val transactionalManager: TransactionalManager,
    private val eventlagerKonverteringsservice: EventlagerKonverteringsservice,
) {
    private val log = LoggerFactory.getLogger(K9PunsjEventHandler::class.java)

    companion object {
        private val typer = BehandlingType.values().filter { it.kodeverk == "PUNSJ_INNSENDING_TYPE" }
    }

    @WithSpan
    fun prosesser(event: PunsjEventDto) {
        EventHandlerMetrics.time("k9punsj", "gjennomført") {
            log.info(event.safePrint())

            val modell = transactionalManager.transaction { tx ->
                val lås = punsjEventK9Repository.hentMedLås(tx, event.eksternId)

                log.info(event.safePrint())
                val modell = punsjEventK9Repository.lagre(event = event, tx)

                eventlagerKonverteringsservice.konverterOppgave(event.eksternId.toString(), Fagsystem.PUNSJ, tx)
                modell
            }

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
