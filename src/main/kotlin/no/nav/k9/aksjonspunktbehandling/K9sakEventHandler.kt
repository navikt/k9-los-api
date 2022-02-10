package no.nav.k9.aksjonspunktbehandling

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.IModell
import no.nav.k9.domene.modell.K9SakModell
import no.nav.k9.domene.modell.reportMetrics
import no.nav.k9.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.domene.repository.OppgaveKøRepository
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.ReservasjonRepository
import no.nav.k9.domene.repository.StatistikkRepository
import no.nav.k9.integrasjon.datavarehus.StatistikkProducer
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import no.nav.k9.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
import org.slf4j.LoggerFactory


class K9sakEventHandler constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val oppgaveTjenesteK9: OppgaveTjenesteSak,
    private val behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    private val sakOgBehandlingProducer: SakOgBehandlingProducer,
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val reservasjonRepository: ReservasjonRepository,
    private val statistikkProducer: StatistikkProducer,
    private val statistikkChannel: Channel<Boolean>,
    private val statistikkRepository: StatistikkRepository,
    private val reservasjonTjeneste: ReservasjonTjeneste,
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

    fun prosesser(
        event: BehandlingProsessEventDto
    ) {
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

        val oppgave = modell.oppgave(modell.sisteEvent())
        oppgaveRepository.lagre(oppgave.eksternId) {
            tellEvent(modell, oppgave)
            statistikkProducer.send(modell)
            oppgave
        }
        if (modell.fikkEndretAksjonspunkt()) {
            log.info("Fjerner reservasjon på oppgave ${oppgave.eksternId}")
            reservasjonTjeneste.fjernReservasjon(oppgave)
        }
        modell.reportMetrics(reservasjonRepository)
        runBlocking {
            for (oppgavekø in oppgaveKøRepository.hentKøIdIkkeTaHensyn()) {
                oppgaveKøRepository.leggTilOppgaverTilKø(oppgavekø, listOf(oppgave), reservasjonRepository)
            }
            statistikkChannel.send(true)
        }
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
        if (k9SakModell.starterSak() && !oppgave.aksjonspunkter.erIngenAktive() && !oppgave.aksjonspunkter.påVent()) {
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
