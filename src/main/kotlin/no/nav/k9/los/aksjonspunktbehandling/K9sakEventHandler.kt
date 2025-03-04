package no.nav.k9.los.aksjonspunktbehandling

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.modell.BehandlingStatus
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.modell.K9SakModell
import no.nav.k9.los.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.utils.OpentelemetrySpanUtil
import org.slf4j.LoggerFactory


class K9sakEventHandler (
    private val behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    private val statistikkChannel: Channel<Boolean>,
    private val k9SakTilLosAdapterTjeneste: K9SakTilLosAdapterTjeneste,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) {
    private val log = LoggerFactory.getLogger(K9sakEventHandler::class.java)

    @WithSpan
    fun prosesser(
        eventInn: BehandlingProsessEventDto
    ) {
        val t0 = System.nanoTime()

        val event = håndterVaskeevent(eventInn)
            ?: return

        var skalSkippe = false
        val modell = behandlingProsessEventK9Repository.lagre(event.eksternId!!) { k9SakModell ->
            if (k9SakModell == null) {
                return@lagre K9SakModell(mutableListOf(event))
            }
            if (k9SakModell.eventer.contains(event)) {
                log.info("""Skipping eventen har kommet tidligere ${event.eventTid}""")
                skalSkippe = true
                return@lagre k9SakModell
            }
            k9SakModell.eventer.add(event)
            k9SakModell
        }

        if (skalSkippe) {
            return
        }

        runBlocking {
            statistikkChannel.send(true)
        }

        OpentelemetrySpanUtil.span("k9SakTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid") {
            k9SakTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid(event.eksternId)
        }

        runBlocking {
            køpåvirkendeHendelseChannel.send(OppgaveHendelseMottatt(Fagsystem.K9SAK, EksternOppgaveId("K9", event.eksternId.toString())))
        }
        EventHandlerMetrics.observe("k9sak", "gjennomført", t0)
    }

    fun håndterVaskeevent(event: BehandlingProsessEventDto): BehandlingProsessEventDto? {
        if (event.eventHendelse != EventHendelse.VASKEEVENT) {
            return event
        }

        // Gjøres utenfor transaksjon fordi den ikke muterer data.
        // Det er ikke mulig å unngå lagring selv om eventet skal ignoreres hvis den skulle ha vært i samme transaksjon (pga on conflict(id) update.. ) i lagre-metoden
        val eksisterendeEventModell = behandlingProsessEventK9Repository.hent(event.eksternId!!)
        if (eksisterendeEventModell.eventer.any { tidligereEvent -> tidligereEvent.behandlingStatus == BehandlingStatus.AVSLUTTET.kode }) {
            return null
        }

        if (eksisterendeEventModell.eventer.isEmpty()) {
            log.info("Vaskeeventfiltrering gjelder behandling som ikke tidligere finnes i los ${event.eksternId}")
            return event
        }

        log.info("Vaskeeventfiltrering ${event.behandlingStatus} - ${event.eventTid} - ${event.eksternId}")
        return eksisterendeEventModell.sisteEvent().eventTid
            .takeIf { sisteEventTid -> sisteEventTid.isAfter(event.eventTid) }
            ?.let { sisteEventTid -> event.copy(eventTid = sisteEventTid.plusNanos(100_1000)) }
            ?: event
    }
}
