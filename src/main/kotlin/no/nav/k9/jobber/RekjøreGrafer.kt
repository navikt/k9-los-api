package no.nav.k9.jobber

import io.ktor.server.application.Application
import io.ktor.server.application.log
import io.ktor.util.logging.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.modell.BehandlingType
import no.nav.k9.domene.modell.FagsakYtelseType
import no.nav.k9.domene.modell.IModell
import no.nav.k9.domene.modell.K9PunsjModell
import no.nav.k9.domene.repository.BehandlingProsessEventK9Repository
import no.nav.k9.domene.repository.BehandlingProsessEventTilbakeRepository
import no.nav.k9.domene.repository.OppgaveKøRepository
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.PunsjEventK9Repository
import no.nav.k9.domene.repository.StatistikkRepository
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import no.nav.k9.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
import java.util.*
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis


fun Application.rekjørEventerForGrafer(
    behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    statistikkRepository: StatistikkRepository
) {

    launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
        try {
            val tillatteYtelseTyper = listOf(
                FagsakYtelseType.OMSORGSPENGER,
                FagsakYtelseType.PLEIEPENGER_SYKT_BARN,
                FagsakYtelseType.OMSORGSPENGER_KS,
                FagsakYtelseType.OMSORGSPENGER_MA,
                FagsakYtelseType.OMSORGSPENGER_AO,
                FagsakYtelseType.OMSORGSDAGER,
                FagsakYtelseType.PPN
            )

            val alleEventerIder = behandlingProsessEventK9Repository.hentAlleEventerIder()
            statistikkRepository.truncateStatistikk()
            for ((index, eventId) in alleEventerIder.withIndex()) {
                val alleVersjoner = behandlingProsessEventK9Repository.hent(eventId).alleVersjoner()
                for ((index2, modell) in alleVersjoner.withIndex()) {
                    try {
                        val oppgave = modell.oppgave()

                        if (modell.starterSak() && !oppgave.aksjonspunkter.erIngenAktive() && !oppgave.aksjonspunkter.påVent()) {
                            beholdningOpp(oppgave, statistikkRepository, tillatteYtelseTyper)
                        }
                        if (modell.forrigeEvent() != null && !modell.oppgave(modell.forrigeEvent()!!).aktiv && modell.oppgave().aktiv) {
                            beholdningOpp(oppgave, statistikkRepository, tillatteYtelseTyper)
                        }
                        if (modell.forrigeEvent() != null && modell.oppgave(modell.forrigeEvent()!!).aktiv && !modell.oppgave().aktiv) {
                            beholdingNed(oppgave, statistikkRepository, tillatteYtelseTyper)
                        }

                        if (oppgave.behandlingStatus == BehandlingStatus.AVSLUTTET) {
                            if (!oppgave.ansvarligSaksbehandlerForTotrinn.isNullOrBlank()) {
                                nyFerdigstilltAvSaksbehandler(oppgave, statistikkRepository, tillatteYtelseTyper)
                            }
                        }
                    } catch (e: Exception) {
                        continue
                    } finally {
                        if (index2 % 100 == 0 && index2 > 1) {
                            log.info("""Ferdig med $index2 av ${alleEventerIder.size}""")
                        }
                    }
                }
                if (index % 100 == 0 && index > 1) {
                    log.info("""Ferdig med $index av ${alleEventerIder.size}""")
                }
            }
            log.info("""Ferdig med ${alleEventerIder.size} av ${alleEventerIder.size}""")
        } catch (e: Exception) {
            log.error(e)
        }
    }
}

fun Application.rekjørEventerForGraferFraPunsj(
    punsjEventRepo: PunsjEventK9Repository,
    statistikkRepository: StatistikkRepository
) {

    launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
        val typer = BehandlingType.values().filter { it.kodeverk == "PUNSJ_INNSENDING_TYPE" }.map { it.kode }
        log.info("""Starter jobb rekjørEventerForGraferFraPunsj""")

        val slettAltFraPunsj = statistikkRepository.slettAltFraPunsj()
        log.info("""slettet antall=$slettAltFraPunsj""")
        try {
            val alleEventerIder = punsjEventRepo.hentAlleEventerIder()
            for ((index, eventId) in alleEventerIder.withIndex()) {
                if (index % 100 == 0 && index > 1) {
                    log.info("""Punsj: Ferdig med $index av ${alleEventerIder.size}""")
                }
                val alleVersjoner = punsjEventRepo.hent(UUID.fromString(eventId)).alleVersjoner()
                for (k9PunsjModell in alleVersjoner) {
                    try {
                        val oppgave = k9PunsjModell.oppgave()
                        val kode = K9PunsjModell(listOf(k9PunsjModell.eventer[0])).oppgave().behandlingType.kode
                        if (typer.contains(kode)) {
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
                            } else if (k9PunsjModell.eventer.size > 1 && !oppgave.aktiv && (k9PunsjModell.forrigeEvent() != null && k9PunsjModell.oppgave(
                                    k9PunsjModell.forrigeEvent()!!
                                ).aktiv)
                            ) {
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
                    } catch (e: Exception) {
                        log.info("""Feilet med denne feilen ${e.message}""")
                        continue
                    }
                }
            }
            log.info("""Punsj: Ferdig med ${alleEventerIder.size} av ${alleEventerIder.size}""")
        } catch (e: Exception) {
            log.error(e)
        }
    }
}


private fun nyFerdigstilltAvSaksbehandler(
    oppgave: Oppgave,
    statistikkRepository: StatistikkRepository,
    tillatteYtelseTyper: List<FagsakYtelseType>
) {
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

private fun beholdingNed(
    oppgave: Oppgave,
    statistikkRepository: StatistikkRepository,
    tillatteYtelseTyper: List<FagsakYtelseType>
) {
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

private fun beholdningOpp(
    oppgave: Oppgave,
    statistikkRepository: StatistikkRepository,
    tillatteYtelseTyper: List<FagsakYtelseType>
) {
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

fun Application.regenererOppgaver(
    oppgaveRepository: OppgaveRepository,
    behandlingProsessEventK9Repository: BehandlingProsessEventK9Repository,
    punsjEventK9Repository: PunsjEventK9Repository,
    behandlingProsessEventTilbakeRepository: BehandlingProsessEventTilbakeRepository,
    oppgaveKøRepository: OppgaveKøRepository,
    reservasjonTjeneste: ReservasjonTjeneste
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
                        modell = punsjEventK9Repository.hent(aktivOppgave.eksternId)
                    }
                    // finner ikke i punsj, sjekker mot tilbake
                    if (modell.erTom()) {
                        modell = behandlingProsessEventTilbakeRepository.hent(aktivOppgave.eksternId)
                    }
                    // finner den ikke i det hele tatt
                    if (modell.erTom()) {
                        log.error("""Finner ikke modell for oppgave ${aktivOppgave.eksternId} setter oppgaven til inaktiv""")
                        oppgaveRepository.lagre(aktivOppgave.eksternId) { oppgave ->
                            oppgave!!.copy(aktiv = false)
                        }
                        continue
                    }
                    var oppgave: Oppgave?
                    try {
                        oppgave = modell.oppgave()

                    } catch (e: Exception) {
                        log.error("""Mismatch mellom gamel og ny kontrakt""", e)
                        continue
                    }
                    if (oppgave.aktiv) {
                        reservasjonTjeneste.fjernReservasjon(oppgave)
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
