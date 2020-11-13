package no.nav.k9.jobber

import io.ktor.application.*
import io.ktor.util.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.IModell
import no.nav.k9.domene.repository.*
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import java.util.*
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis


fun Application.rekjørEventerForGrafer(
    behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    statistikkRepository: StatistikkRepository,
    reservasjonRepository: ReservasjonRepository
) {
    launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
        try {
            val alleEventerIder = behandlingProsessEventK9Repository.hentAlleEventerIder()
            statistikkRepository.truncateStatistikk()
            for ((index, eventId) in alleEventerIder.withIndex()) {
                if (index % 100 == 0 && index > 1) {
                    log.info("""Ferdig med $index av ${alleEventerIder.size}""")
                }
                val alleVersjoner = behandlingProsessEventK9Repository.hent(UUID.fromString(eventId)).alleVersjoner()
                for ((index, modell) in alleVersjoner.withIndex()) {
                    if (index % 100 == 0 && index > 1) {
                        log.info("""Ferdig med $index av ${alleEventerIder.size}""")
                    }
                    try {
                        val oppgave = modell.oppgave()

                        if (modell.starterSak()) {
                            if (oppgave.aktiv) {
                                beholdningOpp(oppgave, statistikkRepository)
                            }
                        }
                        if (modell.forrigeEvent() != null && !modell.oppgave(modell.forrigeEvent()!!).aktiv && modell.oppgave().aktiv) {
                            beholdningOpp(oppgave, statistikkRepository)
                        }

                        if (modell.forrigeEvent() != null && modell.oppgave(modell.forrigeEvent()!!).aktiv && !modell.oppgave().aktiv) {
                            beholdingNed(oppgave, statistikkRepository)
                        }

                        if (oppgave.behandlingStatus == BehandlingStatus.AVSLUTTET) {
                            if (!oppgave.ansvarligSaksbehandlerForTotrinn.isNullOrBlank()) {
                                nyFerdigstilltAvSaksbehandler(oppgave, statistikkRepository)
                            }
                        }
                    } catch (e: Exception) {
                        continue
                    }
                }
            }
            log.info("""Ferdig med ${alleEventerIder.size} av ${alleEventerIder.size}""")
        } catch (e: Exception) {
            log.error(e)
        }
    }
}


private fun nyFerdigstilltAvSaksbehandler(oppgave: Oppgave, statistikkRepository: StatistikkRepository) {
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

private fun beholdingNed(oppgave: Oppgave, statistikkRepository: StatistikkRepository) {
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

private fun beholdningOpp(oppgave: Oppgave, statistikkRepository: StatistikkRepository) {
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

@KtorExperimentalAPI
fun Application.regenererOppgaver(
    oppgaveRepository: OppgaveRepository,
    behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    punsjEventK9Repository: PunsjEventK9Repository,
    behandlingProsessEventTilbakeRepository: BehandlingProsessEventTilbakeRepository,
    reservasjonRepository: ReservasjonRepository,
    oppgaveKøRepository: OppgaveKøRepository,
    saksbehhandlerRepository: SaksbehandlerRepository
) {
    launch(context = Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
        try {

            log.info("Starter oppgavesynkronisering")
            val measureTimeMillis = measureTimeMillis {
                val hentAktiveOppgaver = oppgaveRepository.hentAktiveOppgaver()
                for ((index, aktivOppgave) in hentAktiveOppgaver.withIndex()) {
                    var modell: IModell = behandlingProsessEventK9Repository.hent(aktivOppgave.eksternId)

                    //finner ikke i k9, sjekker mot punsj
                    if (modell.erTom()) {
                        modell = punsjEventK9Repository.hent(aktivOppgave.eksternId);
                    }
                    // finner ikke i punsj, sjekker mot tilbake
                    if (modell.erTom()) {
                        modell = behandlingProsessEventTilbakeRepository.hent(aktivOppgave.eksternId);
                    }
                    // finner den ikke i det hele tatt
                    if (modell.erTom()) {
                        log.error("""Finner ikke modell for oppgave ${aktivOppgave.eksternId} setter oppgaven til inaktiv""")
                        oppgaveRepository.lagre(aktivOppgave.eksternId) {
                            it!!.copy(aktiv = false)
                            it
                        }
                    }
                    val oppgave = modell.oppgave()
                    if (!oppgave.aktiv) {
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
                    oppgaveRepository.lagre(oppgave.eksternId) {
                        oppgave
                    }
                    if (index % 10 == 0) {
                        log.info("Synkronisering " + index + " av " + hentAktiveOppgaver.size)
                    }
                }
                for (oppgavekø in oppgaveKøRepository.hentIkkeTaHensyn()) {
                    oppgaveKøRepository.oppdaterKøMedOppgaver(oppgavekø.id)
                }
            }
            log.info("Avslutter oppgavesynkronisering: $measureTimeMillis ms")
        } catch (e: Exception) {
            log.error("", e)
        }
    }
}
