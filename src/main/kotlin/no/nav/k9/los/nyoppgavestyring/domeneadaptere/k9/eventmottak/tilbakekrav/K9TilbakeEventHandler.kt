package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.tilbakekrav

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHandlerMetrics
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.tilbaketillos.K9TilbakeTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.modia.SakOgBehandlingProducer
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import org.slf4j.LoggerFactory


class K9TilbakeEventHandler(
    private val behandlingProsessEventTilbakeRepository: K9TilbakeEventRepository,
    private val sakOgBehandlingProducer: SakOgBehandlingProducer,
    private val k9TilbakeTilLosAdapterTjeneste : K9TilbakeTilLosAdapterTjeneste,
) {

    companion object {
        private val log = LoggerFactory.getLogger(K9TilbakeEventHandler::class.java)
    }

    @WithSpan
    fun prosesser(
        event: K9TilbakeEventDto
    ) {
        EventHandlerMetrics.time("k9tilbake", "gjennomført") {
            val modell = behandlingProsessEventTilbakeRepository.lagre(event)

            sendModia(modell)

            OpentelemetrySpanUtil.span("k9TilbakeTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid") {
                try {
                    k9TilbakeTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid(
                        event.eksternId!!
                    )
                } catch (e: Exception) {
                    log.error("Oppatering av k9-tilbake-oppgave feilet for ${event.eksternId}. Oppgaven er ikke oppdatert, men blir plukket av vaktmester", e)
                }
            }
        }
    }

    private fun sendModia(
        modell: K9TilbakeModell,
    ) {
        val k9TilbakeModell = modell as K9TilbakeModell
        if (k9TilbakeModell.starterSak()) {
            sakOgBehandlingProducer.behandlingOpprettet(k9TilbakeModell.behandlingOpprettetSakOgBehandling())
        }

        if (modell.sisteEvent().behandlingStatus == BehandlingStatus.AVSLUTTET.kode) {
            sakOgBehandlingProducer.avsluttetBehandling(k9TilbakeModell.behandlingAvsluttetSakOgBehandling())
        }
    }
}