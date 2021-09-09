package no.nav.k9.aksjonspunktbehandling

import io.ktor.util.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.Configuration
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.K9SakModell
import no.nav.k9.domene.repository.*
import no.nav.k9.integrasjon.datavarehus.StatistikkProducer
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventDto
import no.nav.k9.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import org.slf4j.LoggerFactory
import reportMetrics


class K9sakEventHandler constructor(
    val oppgaveRepository: OppgaveRepository,
    val behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    val config: Configuration,
    val sakOgBehandlingProducer: SakOgBehandlingProducer,
    val oppgaveKøRepository: OppgaveKøRepository,
    val reservasjonRepository: ReservasjonRepository,
    val statistikkProducer: StatistikkProducer,
    val statistikkChannel: Channel<Boolean>,
    val statistikkRepository: StatistikkRepository,
    val saksbehhandlerRepository: SaksbehandlerRepository
) {
    private val log = LoggerFactory.getLogger(K9sakEventHandler::class.java)

    val tillatteYtelseTyper = listOf(
        FagsakYtelseType.OMSORGSPENGER,
        FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
        FagsakYtelseType.OMSORGSPENGER_KS,
        FagsakYtelseType.OMSORGSPENGER_MA,
        FagsakYtelseType.OMSORGSPENGER_AO
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
            beholdningOppNed(modell, oppgave)
            statistikkProducer.send(modell)
            oppgave
        }
        if (modell.fikkEndretAksjonspunkt()) {
            fjernReservasjon(oppgave)
        }
        modell.reportMetrics(reservasjonRepository)
        runBlocking {
            for (oppgavekø in oppgaveKøRepository.hentKøIdIkkeTaHensyn()) {
                oppgaveKøRepository.leggTilOppgaverTilKø(oppgavekø, listOf(oppgave), reservasjonRepository)
            }
            statistikkChannel.send(true)

        }
    }

    private fun beholdningOppNed(
        modell: K9SakModell,
        oppgave: Oppgave
    ) {
        if (modell.starterSak()) {
            sakOgBehandlingProducer.behandlingOpprettet(modell.behandlingOpprettetSakOgBehandling())
            beholdningOpp(oppgave)
        }
        if (modell.forrigeEvent() != null && !modell.oppgave(modell.forrigeEvent()!!).aktiv && modell.oppgave(modell.sisteEvent()).aktiv) {
            beholdningOpp(oppgave)
        } else if (modell.forrigeEvent() != null && modell.oppgave(modell.forrigeEvent()!!).aktiv && !modell.oppgave(
                modell.sisteEvent()
            ).aktiv
        ) {
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

            sakOgBehandlingProducer.avsluttetBehandling(modell.behandlingAvsluttetSakOgBehandling())
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
        }
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
        }
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
        }
    }


    private fun fjernReservasjon(oppgave: Oppgave) {
        if (reservasjonRepository.finnes(oppgave.eksternId)) {
            reservasjonRepository.lagre(oppgave.eksternId) { reservasjon ->
                reservasjon!!.reservertTil = null
                reservasjon
            }
            val reservasjon = reservasjonRepository.hent(oppgave.eksternId)
            saksbehhandlerRepository.fjernReservasjonIkkeTaHensyn(
                reservasjon.reservertAv,
                reservasjon.oppgave
            )
        }
    }

}
