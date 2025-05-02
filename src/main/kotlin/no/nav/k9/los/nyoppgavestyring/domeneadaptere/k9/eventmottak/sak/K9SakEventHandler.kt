package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHandlerMetrics
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.modia.SakOgBehandlingProducer
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import org.slf4j.LoggerFactory


class K9SakEventHandler (
    private val k9SakEventRepository: K9SakEventRepository,
    private val sakOgBehandlingProducer: SakOgBehandlingProducer,
    private val k9SakTilLosAdapterTjeneste: K9SakTilLosAdapterTjeneste,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) {
    private val log = LoggerFactory.getLogger(K9SakEventHandler::class.java)

    private val tillatteYtelseTyper = listOf(
        FagsakYtelseType.OMSORGSPENGER,
        FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
        FagsakYtelseType.OMSORGSPENGER_KS,
        FagsakYtelseType.OMSORGSPENGER_MA,
        FagsakYtelseType.OMSORGSPENGER_AO,
        FagsakYtelseType.OMSORGSDAGER,
        FagsakYtelseType.PPN
    )

    @WithSpan
    fun prosesser(
        eventInn: K9SakEventDto
    ) {
        val t0 = System.nanoTime()

        val event = håndterVaskeevent(eventInn)
        if (event == null) {
            EventHandlerMetrics.observe("k9sak", "vaskeevent", t0)
            return
        }

        var skalSkippe = false
        val modell = k9SakEventRepository.lagre(event.eksternId!!) { k9SakModell ->
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
            EventHandlerMetrics.observe("k9sak", "skipper", t0)
            return
        }

        sendModia(modell)

        OpentelemetrySpanUtil.span("k9SakTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid") {
            k9SakTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid(event.eksternId)
        }

        runBlocking {
            køpåvirkendeHendelseChannel.send(
                OppgaveHendelseMottatt(
                    Fagsystem.K9SAK,
                    EksternOppgaveId("K9", event.eksternId.toString())
                )
            )
        }
        EventHandlerMetrics.observe("k9sak", "gjennomført", t0)
    }

    private fun sendModia(
        modell: K9SakModell,
    ) {
        val k9SakModell = modell as K9SakModell
        if (k9SakModell.starterSak()) {
            sakOgBehandlingProducer.behandlingOpprettet(k9SakModell.behandlingOpprettetSakOgBehandling())
        }

        if (modell.sisteEvent().behandlingStatus == BehandlingStatus.AVSLUTTET.kode) {
            sakOgBehandlingProducer.avsluttetBehandling(k9SakModell.behandlingAvsluttetSakOgBehandling())
        }
    }

    fun håndterVaskeevent(event: K9SakEventDto): K9SakEventDto? {
        if (event.eventHendelse != EventHendelse.VASKEEVENT) {
            return event
        }

        // Gjøres utenfor transaksjon fordi den ikke muterer data.
        // Det er ikke mulig å unngå lagring selv om eventet skal ignoreres hvis den skulle ha vært i samme transaksjon (pga on conflict(id) update.. ) i lagre-metoden
        val eksisterendeEventModell = k9SakEventRepository.hent(event.eksternId!!)
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