package no.nav.k9.los.aksjonspunktbehandling

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.domene.repository.*
import no.nav.k9.los.integrasjon.kafka.dto.BehandlingProsessEventTilbakeDto
import no.nav.k9.los.integrasjon.sakogbehandling.SakOgBehandlingProducer
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.reservasjonkonvertering.ReservasjonOversetter
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import no.nav.k9.los.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
import org.slf4j.LoggerFactory


class K9TilbakeEventHandler(
    private val oppgaveRepository: OppgaveRepository,
    private val behandlingProsessEventTilbakeRepository: BehandlingProsessEventTilbakeRepository,
    private val sakOgBehandlingProducer: SakOgBehandlingProducer,
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val reservasjonRepository: ReservasjonRepository,
    private val statistikkRepository: StatistikkRepository,
    private val statistikkChannel: Channel<Boolean>,
    private val reservasjonTjeneste: ReservasjonTjeneste,
    private val reservasjonV3Tjeneste: ReservasjonV3Tjeneste,
    private val reservasjonOversetter: ReservasjonOversetter,
    private val saksbehandlerRepository: SaksbehandlerRepository,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) : EventTeller {

    companion object {
        private val log = LoggerFactory.getLogger(K9TilbakeEventHandler::class.java)
    }

    fun prosesser(
        event: BehandlingProsessEventTilbakeDto
    ) {
        EventHandlerMetrics.time("k9tilbake", "gjennomført") {
            val modell = behandlingProsessEventTilbakeRepository.lagre(event)
            val oppgave = modell.oppgave(modell.sisteEvent())

            oppgaveRepository.lagre(oppgave.eksternId) {
                tellEvent(modell, oppgave)
                oppgave
            }

            if (modell.fikkEndretAksjonspunkt()) {
                log.info("Fjerner reservasjon på oppgave ${oppgave.eksternId}")
                reservasjonTjeneste.fjernReservasjon(oppgave)
                val reservasjonV3 =
                    reservasjonOversetter.hentAktivReservasjonFraGammelKontekst(oppgave)
                reservasjonV3?.let {
                    val identSomAnnullerer = if (oppgave.tilBeslutter) { event.ansvarligSaksbehandlerIdent } else { event.ansvarligBeslutterIdent ?: event.ansvarligSaksbehandlerIdent }
                    identSomAnnullerer?.let {
                        val saksbehandlerSomAnnullerer = runBlocking { saksbehandlerRepository.finnSaksbehandlerMedIdent(identSomAnnullerer)!! }
                        reservasjonV3Tjeneste.annullerReservasjonHvisFinnes( reservasjonV3.reservasjonsnøkkel, "Tilbakekrav - annullerer ", saksbehandlerSomAnnullerer.id!!)
                    }
                }
            }

            runBlocking {
                for (oppgavekø in oppgaveKøRepository.hentKøIdIkkeTaHensyn()) {
                    oppgaveKøRepository.leggTilOppgaverTilKø(oppgavekø, listOf(oppgave), reservasjonRepository)
                }
                statistikkChannel.send(true)
                køpåvirkendeHendelseChannel.send(OppgaveHendelseMottatt(Fagsystem.K9TILBAKE, EksternOppgaveId("K9", event.eksternId.toString())))
            }
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

    override fun tellEvent(modell: IModell, oppgave: Oppgave) {
        val k9TilbakeModell = modell as K9TilbakeModell
        if (k9TilbakeModell.starterSak()) {
            sakOgBehandlingProducer.behandlingOpprettet(k9TilbakeModell.behandlingOpprettetSakOgBehandling())
            beholdningOpp(oppgave)
        }
        if (k9TilbakeModell.forrigeEvent() != null && !k9TilbakeModell.oppgave(k9TilbakeModell.forrigeEvent()!!).aktiv && k9TilbakeModell.oppgave(k9TilbakeModell.sisteEvent()).aktiv) {
            beholdningOpp(oppgave)
        } else if (k9TilbakeModell.forrigeEvent() != null && k9TilbakeModell.oppgave(k9TilbakeModell.forrigeEvent()!!).aktiv && !k9TilbakeModell.oppgave(
                k9TilbakeModell.sisteEvent()
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
            sakOgBehandlingProducer.avsluttetBehandling(k9TilbakeModell.behandlingAvsluttetSakOgBehandling())
        }
    }
}
