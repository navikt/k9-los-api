package no.nav.k9.jobber

import io.ktor.application.*
import io.ktor.util.*
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.launch
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.modell.BehandlingStatus
import no.nav.k9.domene.repository.PunsjEventK9Repository
import no.nav.k9.domene.repository.ReservasjonRepository
import no.nav.k9.domene.repository.StatistikkRepository
import no.nav.k9.integrasjon.kafka.dto.Fagsystem
import no.nav.k9.tjenester.avdelingsleder.nokkeltall.AlleOppgaverNyeOgFerdigstilte
import java.util.*
import java.util.concurrent.Executors


fun Application.rekjørEventerForGraferPunsj(
    punsjEventK9Repository: PunsjEventK9Repository,
    statistikkRepository: StatistikkRepository
) {

    launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
        try {
            val alleEventerIder = punsjEventK9Repository.hentAlleEventerIder()
            statistikkRepository.fjernDataFraSystem(Fagsystem.PUNSJ);
            for ((index, eventId) in alleEventerIder.withIndex()) {
                if (index % 100 == 0 && index > 1) {
                    log.info("""Ferdig med $index av ${alleEventerIder.size}""")
                }
                val alleVersjoner = punsjEventK9Repository.hent(UUID.fromString(eventId)).alleVersjoner()
                for ((index, modell) in alleVersjoner.withIndex()) {
                    if (index % 100 == 0 && index > 1) {
                        log.info("""Ferdig med $index av ${alleEventerIder.size}""")
                    }
                    try {
                        val oppgave = modell.oppgave()
                        if (modell.starterSak()) {
                            beholdningOpp(oppgave, statistikkRepository)
                        } else if (oppgave.behandlingStatus == BehandlingStatus.LUKKET || oppgave.behandlingStatus == BehandlingStatus.SENDT_INN) {
                            beholdingNed(oppgave, statistikkRepository)
                            nyFerdigstilltAvSaksbehandler(oppgave, statistikkRepository)
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

private fun nyFerdigstilltAvSaksbehandler(
    oppgave: Oppgave,
    statistikkRepository: StatistikkRepository,

    ) {
    statistikkRepository.lagre(
        AlleOppgaverNyeOgFerdigstilte(
            oppgave.fagsakYtelseType,
            oppgave.behandlingType,
            oppgave.eventTid.toLocalDate(),
            Fagsystem.PUNSJ
        )
    ) {
        it.ferdigstilteSaksbehandler.add(oppgave.eksternId.toString())
        it
    }
}

private fun beholdingNed(oppgave: Oppgave, statistikkRepository: StatistikkRepository) {
    statistikkRepository.lagre(
        AlleOppgaverNyeOgFerdigstilte(
            oppgave.fagsakYtelseType,
            oppgave.behandlingType,
            oppgave.eventTid.toLocalDate(),
            Fagsystem.PUNSJ
        )
    ) {
        it.ferdigstilte.add(oppgave.eksternId.toString())
        it
    }
}

private fun beholdningOpp(oppgave: Oppgave, statistikkRepository: StatistikkRepository) {
    statistikkRepository.lagre(
        AlleOppgaverNyeOgFerdigstilte(
            oppgave.fagsakYtelseType,
            oppgave.behandlingType,
            oppgave.eventTid.toLocalDate(),
            Fagsystem.PUNSJ
        )
    ) {
        it.nye.add(oppgave.eksternId.toString())
        it
    }
}
