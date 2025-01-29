package no.nav.k9.los.aksjonspunktbehandling

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.domene.repository.*
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.los.integrasjon.kafka.dto.EventHendelse
import no.nav.k9.los.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import no.nav.k9.los.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
import no.nav.k9.los.utils.OpentelemetrySpanUtil
import org.slf4j.LoggerFactory


class K9sakEventHandler constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    private val sakOgBehandlingProducer: SakOgBehandlingProducer,
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val reservasjonRepository: ReservasjonRepository,
    private val statistikkChannel: Channel<Boolean>,
    private val statistikkRepository: StatistikkRepository,
    private val k9SakTilLosAdapterTjeneste: K9SakTilLosAdapterTjeneste,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) : EventTeller {
    private val log = LoggerFactory.getLogger(K9sakEventHandler::class.java)

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
        eventInn: BehandlingProsessEventDto
    ) {
        val t0 = System.nanoTime()

        val event = håndterVaskeevent(eventInn)
        if (event == null) {
            EventHandlerMetrics.observe("k9sak", "vaskeevent", t0)
            return
        }

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
        val oppgave = modell.oppgave(modell.sisteEvent())
        oppgaveRepository.lagre(oppgave.eksternId) { oppgave }

        if (skalSkippe) {
            EventHandlerMetrics.observe("k9sak", "skipper", t0)
            return
        }
        tellEvent(modell, oppgave)

        var reservasjonFjernet = false
        if (modell.fikkEndretAksjonspunkt()) {
            log.info("Fjerner reservasjon på oppgave ${oppgave.eksternId}")
            reservasjonTjeneste.fjernReservasjon(oppgave)
            reservasjonFjernet = true
        }
        modell.reportMetrics(reservasjonRepository)
        runBlocking {
            for (oppgavekø in oppgaveKøRepository.hentKøIdInkluderKode6()) {
                if (reservasjonFjernet){
                    oppgaveKøRepository.leggTilOppgaverTilKø(oppgavekø, listOf(oppgave), erOppgavenReservertSjekk = {false}) //reservasjon nettopp fjernet, trenger ikke sjekke mot repository
                } else {
                    oppgaveKøRepository.leggTilOppgaverTilKø(oppgavekø, listOf(oppgave), reservasjonRepository)
                }
            }
            statistikkChannel.send(true)
        }

        OpentelemetrySpanUtil.span("k9SakTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid") { k9SakTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid(event.eksternId) }

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


    private fun nyFerdigstilltAvSaksbehandler(oppgave: Oppgave) {
        if (tillatteYtelseTyper.contains(oppgave.fagsakYtelseType)) {
            statistikkRepository.lagre(
                AlleOppgaverNyeOgFerdigstilte(
                    oppgave.fagsakYtelseType,
                    oppgave.behandlingType,
                    oppgave.eventTid.toLocalDate()
                )
            ) {
                it.ferdigstilteSaksbehandler.add(oppgave.eksternId.toString())
                it
            }
        } else log.warn("Ignorerer nyFerdigstilltAvSaksbehandler statistikk på ytelseType=${oppgave.fagsakYtelseType} da den ikke er støttet enda ")
    }

    private fun beholdingNed(oppgave: Oppgave) {
        if (tillatteYtelseTyper.contains(oppgave.fagsakYtelseType)) {
            statistikkRepository.lagre(
                AlleOppgaverNyeOgFerdigstilte(
                    oppgave.fagsakYtelseType,
                    oppgave.behandlingType,
                    oppgave.eventTid.toLocalDate()
                )
            ) {
                it.ferdigstilte.add(oppgave.eksternId.toString())
                it
            }
        } else log.warn("Ignorerer beholdingNed statistikk på ytelseType=${oppgave.fagsakYtelseType} da den ikke er støttet enda ")
    }

    private fun beholdningOpp(oppgave: Oppgave) {
        if (tillatteYtelseTyper.contains(oppgave.fagsakYtelseType)) {
            statistikkRepository.lagre(
                AlleOppgaverNyeOgFerdigstilte(
                    oppgave.fagsakYtelseType,
                    oppgave.behandlingType,
                    oppgave.eventTid.toLocalDate()
                )
            ) {
                it.nye.add(oppgave.eksternId.toString())
                it
            }
        } else log.warn("Ignorerer beholdningOpp statistikk på ytelseType=${oppgave.fagsakYtelseType} da den ikke er støttet enda ")
    }

    override fun tellEvent(modell: IModell, oppgave: Oppgave) {
        val k9SakModell = modell as K9SakModell
        if (k9SakModell.starterSak()) {
            sakOgBehandlingProducer.behandlingOpprettet(k9SakModell.behandlingOpprettetSakOgBehandling())
        }

        // teller bare først event hvis det er aksjonspunkt
        if (k9SakModell.starterSak() && !oppgave.aksjonspunkter.erIngenAktive() && !oppgave.aksjonspunkter.påVent(Fagsystem.K9SAK)) {
            beholdningOpp(oppgave)
        }

        if (k9SakModell.forrigeEvent() != null && !k9SakModell.oppgave(k9SakModell.forrigeEvent()!!).aktiv && k9SakModell.oppgave(k9SakModell.sisteEvent()).aktiv) {
            beholdningOpp(oppgave)
        } else if (k9SakModell.forrigeEvent() != null && k9SakModell.oppgave(k9SakModell.forrigeEvent()!!).aktiv && !k9SakModell.oppgave(k9SakModell.sisteEvent()).aktiv) {
            beholdingNed(oppgave)
        }

        if (oppgave.behandlingStatus == BehandlingStatus.AVSLUTTET) {
            if (!oppgave.ansvarligSaksbehandlerForTotrinn.isNullOrBlank()) {
                nyFerdigstilltAvSaksbehandler(oppgave)
                statistikkRepository.lagreFerdigstilt(
                    oppgave.behandlingType.kode,
                    oppgave.eksternId,
                    oppgave.eventTid.toLocalDate()
                )
            }
            sakOgBehandlingProducer.avsluttetBehandling(k9SakModell.behandlingAvsluttetSakOgBehandling())
        }
    }
}
