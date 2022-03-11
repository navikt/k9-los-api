package no.nav.k9.aksjonspunktbehandling

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.lager.oppgave.v2.*
import no.nav.k9.domene.modell.*
import no.nav.k9.domene.repository.*
import no.nav.k9.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import no.nav.k9.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
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
) : EventTeller {
    private val log = LoggerFactory.getLogger(K9punsjEventHandler::class.java)

    companion object {
        private val typer = BehandlingType.values().filter { it.kodeverk == "PUNSJ_INNSENDING_TYPE" }
    }

    fun prosesser(
        event: PunsjEventDto
    ) {
        log.info(event.toString())
        val modell = punsjEventK9Repository.lagre(event = event)
        val oppgave = modell.oppgave()
        oppgaveRepository.lagre(oppgave.eksternId){
            tellEvent(modell, oppgave)
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

//        Kan vente med å lage v2 oppgaver til punsjevent inneholder ansvarligsaksbehandler
//        oppdaterOppgaveV2(event)
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

    private fun oppdaterOppgaveV2(event: PunsjEventDto) {
        val resultat = event.utledStatus()
        val behandlingEndret = BehandlingEndret(
            eksternReferanse = event.eksternId.toString(),
            fagsystem = Fagsystem.PUNSJ,
            ytelseType = event.ytelse?.run { FagsakYtelseType.fraKode(this) } ?: FagsakYtelseType.UKJENT,
            behandlingType = event.type,
            søkersId = event.aktørId?.id?.run { Ident(this, Ident.IdType.AKTØRID) },
            tidspunkt = event.eventTid
        )
        val oppgaveHendelser = when (resultat) {
            BehandlingStatus.LUKKET -> listOf(
                FerdigstillBehandling(
                    tidspunkt = event.eventTid,
                    behandlendeEnhet = null,
                    ansvarligSaksbehandlerIdent = null
                )
            )
            BehandlingStatus.OPPRETTET -> event.aksjonspunktKoderMedStatusListe.map {
                OpprettOppgave(
                    tidspunkt = event.eventTid,
                    oppgaveKode = it.key,
                    frist = null,
                )
            }
            else -> emptyList()
        }

        oppgaveTjenesteV2.nyeOppgaveHendelser(
            eksternId = event.eksternId.toString(),
            listOf(behandlingEndret, *oppgaveHendelser.toTypedArray())
        )
    }
}
