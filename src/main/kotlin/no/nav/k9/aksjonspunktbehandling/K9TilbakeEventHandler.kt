package no.nav.k9.aksjonspunktbehandling

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.K9TilbakeModell
import no.nav.k9.domene.repository.*
import no.nav.k9.integrasjon.kafka.dto.BehandlingProsessEventTilbakeDto
import no.nav.k9.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import no.nav.k9.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
import org.slf4j.LoggerFactory


class K9TilbakeEventHandler(
    private val oppgaveRepository: OppgaveRepository,
    private val behandlingProsessEventTilbakeRepository: BehandlingProsessEventTilbakeRepository,
    private val sakOgBehandlingProducer: SakOgBehandlingProducer,
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val reservasjonRepository: ReservasjonRepository,
    private val statistikkRepository: StatistikkRepository,
    private val statistikkChannel: Channel<Boolean>,
    private val reservasjonTjeneste: ReservasjonTjeneste
) {

    fun prosesser(
        event: BehandlingProsessEventTilbakeDto
    ) {
        val modell = behandlingProsessEventTilbakeRepository.lagre(event)
        val oppgave = modell.oppgave(modell.sisteEvent())

        oppgaveRepository.lagre(oppgave.eksternId) {
            beholdningOppNed(modell, oppgave)
            oppgave
        }

        if (modell.fikkEndretAksjonspunkt()) {
            reservasjonTjeneste.fjernReservasjon(oppgave)
        }

        runBlocking {
            for (oppgavekø in oppgaveKøRepository.hentKøIdIkkeTaHensyn()) {
                oppgaveKøRepository.leggTilOppgaverTilKø(oppgavekø, listOf(oppgave), reservasjonRepository)
            }
            statistikkChannel.send(true)
        }
    }

    private fun beholdningOppNed(
        modell: K9TilbakeModell,
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
        if (oppgave.fagsakYtelseType != FagsakYtelseType.FRISINN) {
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
        if (oppgave.fagsakYtelseType != FagsakYtelseType.FRISINN) {
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
        if (oppgave.fagsakYtelseType != FagsakYtelseType.FRISINN) {
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
}
