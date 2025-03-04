package no.nav.k9.los.aksjonspunktbehandling

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.modell.BehandlingType
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.repository.PunsjEventK9Repository
import no.nav.k9.los.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.utils.OpentelemetrySpanUtil
import org.slf4j.LoggerFactory


class K9punsjEventHandler (
    private val punsjEventK9Repository: PunsjEventK9Repository,
    private val statistikkChannel: Channel<Boolean>,
    private val punsjTilLosAdapterTjeneste: K9PunsjTilLosAdapterTjeneste,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) {
    private val log = LoggerFactory.getLogger(K9punsjEventHandler::class.java)

    companion object {
        private val typer = BehandlingType.values().filter { it.kodeverk == "PUNSJ_INNSENDING_TYPE" }
    }

    @WithSpan
    fun prosesser(event: PunsjEventDto) {
        EventHandlerMetrics.time("k9punsj", "gjennomført") {
            log.info(event.safePrint())
            punsjEventK9Repository.lagre(event = event)


            runBlocking {
                statistikkChannel.send(true)
            }

            OpentelemetrySpanUtil.span("punsjTilLosAdapterTjeneste.oppdaterOppgaveForEksternId") {
                punsjTilLosAdapterTjeneste.oppdaterOppgaveForEksternId(event.eksternId)
            }

            runBlocking {
                køpåvirkendeHendelseChannel.send(OppgaveHendelseMottatt(Fagsystem.PUNSJ, EksternOppgaveId("K9", event.eksternId.toString())))
            }
        }
    }
}
