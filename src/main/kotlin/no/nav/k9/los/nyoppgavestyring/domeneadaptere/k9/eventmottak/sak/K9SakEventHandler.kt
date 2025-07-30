package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak

import com.fasterxml.jackson.databind.JsonNode
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHandlerMetrics
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.modia.SakOgBehandlingProducer
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.UUID


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
        event: String
    ) {
        val t0 = System.nanoTime()

        LosObjectMapper.instance.readTree(event)

        //val event = håndterVaskeevent(eventInn)
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
            try {
                k9SakTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid(event.eksternId)
            } catch (e: Exception) {
                log.error("Oppatering av k9-sak-oppgave feilet for ${event.eksternId}. Oppgaven er ikke oppdatert, men blir plukket av vaktmester", e)
            }
        }

        OpentelemetrySpanUtil.span("k9SakTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid") {
            try {
                k9SakTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid(event.eksternId)
            } catch (e: Exception) {
                log.error("Oppatering av k9-sak-oppgave feilet for ${event.eksternId}. Oppgaven er ikke oppdatert, men blir plukket av vaktmester", e)
            }
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
}