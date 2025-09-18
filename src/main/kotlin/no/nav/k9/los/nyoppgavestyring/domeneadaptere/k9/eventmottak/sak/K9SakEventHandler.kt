package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.sak

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.StatistikkRepository
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.modia.SakOgBehandlingProducer
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHandlerMetrics
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventTeller
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.IModell
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingStatus
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import no.nav.k9.los.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.OpentelemetrySpanUtil
import org.slf4j.LoggerFactory


class K9SakEventHandler constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val k9SakEventRepository: K9SakEventRepository,
    private val sakOgBehandlingProducer: SakOgBehandlingProducer,
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val reservasjonRepository: ReservasjonRepository,
    private val statistikkChannel: Channel<Boolean>,
    private val statistikkRepository: StatistikkRepository,
    private val reservasjonTjeneste: ReservasjonTjeneste,
    private val k9SakTilLosAdapterTjeneste: K9SakTilLosAdapterTjeneste,
) : EventTeller {
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

        OpentelemetrySpanUtil.span("k9SakTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid") {
            try {
                k9SakTilLosAdapterTjeneste.oppdaterOppgaveForBehandlingUuid(event.eksternId)
            } catch (e: Exception) {
                log.error("Oppatering av k9-sak-oppgave feilet for ${event.eksternId}. Oppgaven er ikke oppdatert, men blir plukket av vaktmester", e)
            }
        }
        EventHandlerMetrics.observe("k9sak", "gjennomført", t0)
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
        } else log.info("Ignorerer nyFerdigstilltAvSaksbehandler statistikk på ytelseType=${oppgave.fagsakYtelseType} da den ikke er støttet enda ")
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
        } else log.info("Ignorerer beholdingNed statistikk på ytelseType=${oppgave.fagsakYtelseType} da den ikke er støttet enda ")
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
        } else log.info("Ignorerer beholdningOpp statistikk på ytelseType=${oppgave.fagsakYtelseType} da den ikke er støttet enda ")
    }

    override fun tellEvent(modell: IModell, oppgave: Oppgave) {
        val k9SakModell = modell as K9SakModell
        if (k9SakModell.starterSak()) {
            sakOgBehandlingProducer.behandlingOpprettet(k9SakModell.behandlingOpprettetSakOgBehandling())
        }

        // teller bare først event hvis det er aksjonspunkt
        if (k9SakModell.starterSak() && !oppgave.aksjonspunkter.erIngenAktive() && !oppgave.aksjonspunkter.påVent(
                Fagsystem.K9SAK)) {
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
