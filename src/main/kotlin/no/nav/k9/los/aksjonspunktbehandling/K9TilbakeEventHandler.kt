package no.nav.k9.los.aksjonspunktbehandling

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.repository.BehandlingProsessEventTilbakeRepository
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventTilbakeDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos.K9TilbakeTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.utils.OpentelemetrySpanUtil
import org.slf4j.LoggerFactory


class K9TilbakeEventHandler(
    private val behandlingProsessEventTilbakeRepository: BehandlingProsessEventTilbakeRepository,
    private val statistikkChannel: Channel<Boolean>,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
    private val k9TilbakeTilLosAdapterTjeneste : K9TilbakeTilLosAdapterTjeneste,
) {

    companion object {
        private val log = LoggerFactory.getLogger(K9TilbakeEventHandler::class.java)
    }

    @WithSpan
    fun prosesser(
        event: BehandlingProsessEventTilbakeDto
    ) {
        EventHandlerMetrics.time("k9tilbake", "gjennomført") {
            val modell = behandlingProsessEventTilbakeRepository.lagre(event)

            OpentelemetrySpanUtil.span("k9TilbakeTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid") {
                k9TilbakeTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid(event.eksternId!!)
            }

            runBlocking {
                statistikkChannel.send(true)
                køpåvirkendeHendelseChannel.send(OppgaveHendelseMottatt(Fagsystem.K9TILBAKE, EksternOppgaveId("K9", event.eksternId.toString())))
            }
        }
    }
}
