package no.nav.k9.los.aksjonspunktbehandling

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.domene.lager.oppgave.Oppgave
import no.nav.k9.los.domene.repository.OppgaveKøRepository
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.domene.repository.PunsjEventK9Repository
import no.nav.k9.los.domene.repository.ReservasjonRepository
import no.nav.k9.los.domene.repository.StatistikkRepository
import no.nav.k9.los.domene.lager.oppgave.v2.*
import no.nav.k9.los.domene.modell.*
import no.nav.k9.los.integrasjon.azuregraph.IAzureGraphService
import no.nav.k9.los.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.punsjtillos.K9PunsjTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.ko.KøpåvirkendeHendelse
import no.nav.k9.los.nyoppgavestyring.ko.OppgaveHendelseMottatt
import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import no.nav.k9.los.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
import no.nav.k9.los.utils.OpentelemetrySpanUtil
import org.slf4j.LoggerFactory


class K9punsjEventHandler constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val oppgaveTjenesteV2: OppgaveTjenesteV2,
    private val punsjEventK9Repository: PunsjEventK9Repository,
    private val statistikkChannel: Channel<Boolean>,
    private val reservasjonRepository: ReservasjonRepository,
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val reservasjonTjeneste: ReservasjonTjeneste,
    private val statistikkRepository: StatistikkRepository,
    private val azureGraphService: IAzureGraphService,
    private val punsjTilLosAdapterTjeneste: K9PunsjTilLosAdapterTjeneste,
    private val køpåvirkendeHendelseChannel: Channel<KøpåvirkendeHendelse>,
) : EventTeller {
    private val log = LoggerFactory.getLogger(K9punsjEventHandler::class.java)

    companion object {
        private val typer = BehandlingType.values().filter { it.kodeverk == "PUNSJ_INNSENDING_TYPE" }
    }

    @WithSpan
    fun prosesser(event: PunsjEventDto) {
        EventHandlerMetrics.time("k9punsj", "gjennomført") {
            log.info(event.safePrint())
            val modell = punsjEventK9Repository.lagre(event = event)
            val oppgave = modell.oppgave()
            oppgaveRepository.lagre(oppgave.eksternId) {
                tellEvent(modell, oppgave)
                oppgave
            }

            if (modell.fikkEndretAksjonspunkt()) {
                reservasjonTjeneste.fjernReservasjon(oppgave)
            }

            runBlocking {
                for (oppgavekø in oppgaveKøRepository.hentKøIdInkluderKode6()) {
                    oppgaveKøRepository.leggTilOppgaverTilKø(oppgavekø, listOf(oppgave), reservasjonRepository)
                }
                statistikkChannel.send(true)

                oppdaterOppgaveV2(event, modell)
            }

            OpentelemetrySpanUtil.span("punsjTilLosAdapterTjeneste.oppdaterOppgaveForEksternId") { punsjTilLosAdapterTjeneste.oppdaterOppgaveForEksternId(event.eksternId) }
            runBlocking {
                køpåvirkendeHendelseChannel.send(OppgaveHendelseMottatt(Fagsystem.PUNSJ, EksternOppgaveId("K9", event.eksternId.toString())))
            }
        }
    }

    override fun tellEvent(modell: IModell, oppgave: Oppgave) {
        if (typer.contains(oppgave.behandlingType)) {
            val k9PunsjModell = modell as K9PunsjModell

            // teller oppgave fra punsj hvis det er første event og den er aktiv (P.D.D. er alle oppgaver aktive==true fra punsj)
            if (k9PunsjModell.starterSak() && oppgave.aktiv) {
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
            } else if (k9PunsjModell.eventer.size > 1 && !oppgave.aktiv && (k9PunsjModell.forrigeEvent() != null && k9PunsjModell.oppgave(k9PunsjModell.forrigeEvent()!!).aktiv)) {
                statistikkRepository.lagre(
                    AlleOppgaverNyeOgFerdigstilte(
                        oppgave.fagsakYtelseType,
                        oppgave.behandlingType,
                        oppgave.eventTid.toLocalDate()
                    )
                ) {
                    it.ferdigstilte.add(oppgave.eksternId.toString())
                    it.ferdigstilteSaksbehandler.add(oppgave.eksternId.toString())
                    it
                }
            }
        }
    }

    private suspend fun oppdaterOppgaveV2(event: PunsjEventDto, modell: K9PunsjModell) {
        val oppgavehendelser = mutableSetOf<OppgaveHendelse>()
        val resultat = event.utledStatus()
        oppgavehendelser.add(
            BehandlingEndret(
                eksternReferanse = event.eksternId.toString(),
                fagsystem = Fagsystem.PUNSJ,
                ytelseType = event.ytelse?.run { FagsakYtelseType.fraKode(this) } ?: FagsakYtelseType.UKJENT,
                behandlingType = event.type,
                søkersId = event.aktørId?.id?.run { Ident(this, Ident.IdType.AKTØRID) },
                tidspunkt = event.eventTid
            )
        )

        if (modell.starterSak()) {
            event.aksjonspunktKoderMedStatusListe.map {
                oppgavehendelser.add(
                    OpprettOppgave(
                        tidspunkt = event.eventTid,
                        oppgaveKode = it.key,
                        frist = null,
                    )
                )
            }
        }

        if (resultat == BehandlingStatus.SENDT_INN) {
            val behandlendeEnhet = event.ferdigstiltAv?.run {
                azureGraphService.hentEnhetForBrukerMedSystemToken(event.ferdigstiltAv)
            } ?: "UKJENT".also {
                log.warn("Forventet saksbehandler satt for ${event.safePrint()}. Bruker 'UKJENT' som enhet")
            }

            oppgavehendelser.add(
                FerdigstillBehandling(
                    tidspunkt = event.eventTid,
                    behandlendeEnhet = behandlendeEnhet,
                    ansvarligSaksbehandlerIdent = event.ferdigstiltAv
                )
            )
        }

        if (resultat == BehandlingStatus.LUKKET) {
            oppgavehendelser.add(
                AvbrytOppgave(
                    tidspunkt = event.eventTid,
                    oppgaveKode = null
                )
            )
        }
        oppgaveTjenesteV2.nyeOppgaveHendelser(eksternId = event.eksternId.toString(), oppgavehendelser.toList())
    }
}
