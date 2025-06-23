package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.klage.kodeverk.behandling.oppgavetillos.EventHendelse
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHandlerMetrics
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos.K9KlageTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import org.slf4j.LoggerFactory


class K9KlageEventHandler (
    private val behandlingProsessEventKlageRepository: K9KlageEventRepository,
    private val k9KlageTilLosAdapterTjeneste: K9KlageTilLosAdapterTjeneste,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) {
    private val log = LoggerFactory.getLogger(K9KlageEventHandler::class.java)

    @WithSpan
    fun prosesser(
        eventInn: K9KlageEventDto
    ) {
        val t0 = System.nanoTime()
        val event = håndterVaskeevent(eventInn)
        if (event == null) {
            EventHandlerMetrics.observe("k9klage", "vaskeevent", t0)
            return
        }

        behandlingProsessEventKlageRepository.lagre(event.eksternId!!) { k9KlageModell ->
            if (k9KlageModell == null) {
                return@lagre K9KlageModell(mutableListOf(event))
            }
            if (k9KlageModell.eventer.contains(event)) {
                log.info("""Skipping eventen har kommet tidligere ${event.eventTid}""")
                return@lagre k9KlageModell
            }
            k9KlageModell.eventer.add(event)
            k9KlageModell
        }
        OpentelemetrySpanUtil.span("k9KlageTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid") {
            k9KlageTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid(event.eksternId)
        }
        runBlocking {
            køpåvirkendeHendelseChannel.send(OppgaveHendelseMottatt(Fagsystem.K9KLAGE, EksternOppgaveId("K9", event.eksternId.toString())))
        }
        EventHandlerMetrics.observe("k9klage", "gjennomført", t0)
    }

    fun håndterVaskeevent(event: K9KlageEventDto): K9KlageEventDto? {
        if (event.eventHendelse != EventHendelse.VASKEEVENT) {
            return event
        }

        // Gjøres utenfor transaksjon fordi den ikke muterer data.
        // Det er ikke mulig å unngå lagring selv om eventet skal ignoreres hvis den skulle ha vært i samme transaksjon (pga on conflict(id) update.. ) i lagre-metoden
        val eksisterendeEventModell = behandlingProsessEventKlageRepository.hent(event.eksternId!!)
//        if (eksisterendeEventModell.eventer.any { tidligereEvent -> tidligereEvent.behandlingStatus == BehandlingStatus.AVSLUTTET.kode }) {
//            return null
//        }

        if (eksisterendeEventModell.eventer.isEmpty()) {
            log.info("Vaskeeventfiltrering gjelder behandling som ikke tidligere finnes i los ${event.eksternId}")
            return event
        }

        log.info("Vaskeeventfiltrering ${event.behandlingStatus} - ${event.eventTid} - ${event.eksternId}")
        return eksisterendeEventModell.eventer.last().eventTid
            .takeIf { sisteEventTid -> sisteEventTid.isAfter(event.eventTid) }
            ?.let { sisteEventTid -> K9KlageEventDto(
                eksternId = event.eksternId,
                påklagdBehandlingId = event.påklagdBehandlingId,
                påklagdBehandlingType = event.påklagdBehandlingType,
                fagsystem = event.fagsystem,
                saksnummer = event.saksnummer,
                aktørId = event.aktørId,
                eventTid = sisteEventTid.plusNanos(100_1000),
                behandlingstidFrist = event.behandlingstidFrist,
                eventHendelse = event.eventHendelse,
                behandlingStatus = event.behandlingStatus,
                behandlingSteg = event.behandlingSteg,
                behandlendeEnhet = event.behandlendeEnhet,
                ytelseTypeKode = event.ytelseTypeKode,
                resultatType = event.resultatType,
                behandlingTypeKode = event.behandlingTypeKode,
                opprettetBehandling = event.opprettetBehandling,
                ansvarligSaksbehandler = event.ansvarligSaksbehandler,
                fagsakPeriode = event.fagsakPeriode,
                pleietrengendeAktørId = event.pleietrengendeAktørId,
                relatertPartAktørId = event.relatertPartAktørId,
                ansvarligBeslutter = event.ansvarligBeslutter,
                aksjonspunkttilstander = event.aksjonspunkttilstander,
                vedtaksdato = event.vedtaksdato,
                behandlingsårsaker = event.behandlingsårsaker,
                utenlandstilsnitt = event.utenlandstilsnitt
                )
            } ?: event
    }
}
