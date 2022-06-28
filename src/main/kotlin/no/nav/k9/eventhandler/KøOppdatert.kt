package no.nav.k9.eventhandler

import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import no.nav.k9.domene.lager.oppgave.v2.OppgaveRepositoryV2
import no.nav.k9.domene.repository.OppgaveKøRepository
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.domene.repository.ReservasjonRepository
import no.nav.k9.integrasjon.k9.IK9SakService
import no.nav.k9.kodeverk.Fagsystem
import no.nav.k9.sak.kontrakt.behandling.BehandlingIdDto
import no.nav.k9.sak.kontrakt.behandling.BehandlingIdListe
import no.nav.k9.tjenester.saksbehandler.oppgave.OppgaveTjeneste
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors
import kotlin.system.measureTimeMillis

fun CoroutineScope.køOppdatertProsessor(
    channel: ReceiveChannel<UUID>,
    oppgaveKøRepository: OppgaveKøRepository,
    oppgaveRepository: OppgaveRepository,
    oppgaveRepositoryV2: OppgaveRepositoryV2,
    oppgaveTjeneste: OppgaveTjeneste,
    reservasjonRepository: ReservasjonRepository,
    k9SakService: IK9SakService
) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
    val log = LoggerFactory.getLogger("behandleOppgave")
    for (uuid in channel) {
        try {
            val measureTimeMillis = oppdaterKø(
                oppgaveKøRepository,
                uuid,
                oppgaveRepository,
                oppgaveRepositoryV2,
                reservasjonRepository,
                oppgaveTjeneste,
                k9SakService
            )
            log.info("tok ${measureTimeMillis}ms å oppdatere køen: $uuid")

        } catch (e: Exception) {
            log.error("Feilet ved oppdatering av kø $uuid", e)
        }
    }
}

private suspend fun oppdaterKø(
    oppgaveKøRepository: OppgaveKøRepository,
    it: UUID,
    oppgaveRepository: OppgaveRepository,
    oppgaveRepositoryV2: OppgaveRepositoryV2,
    reservasjonRepository: ReservasjonRepository,
    oppgaveTjeneste: OppgaveTjeneste,
    k9SakService: IK9SakService
): Long {
    return measureTimeMillis {
        val kø = oppgaveKøRepository.hentOppgavekø(it)
        val opprinnelige = kø.oppgaverOgDatoer.toMutableList()

        // dersom den er uendret når vi skal lagre, foreta en check og eventuellt lagre på nytt inne i lås
        val aktiveOppgaver = oppgaveRepository.hentAktiveOppgaver()
            .filter { !kø.erOppgavenReservert(reservasjonRepository, it) }
        kø.oppgaverOgDatoer.clear()
        for (oppgave in aktiveOppgaver) {
            if (kø.kode6 == oppgave.kode6) {
                kø.leggOppgaveTilEllerFjernFraKø(
                    oppgave = oppgave,
                    merknader = oppgaveRepositoryV2.hentMerknader(oppgave.eksternId.toString())
                )
            }
        }
        val behandlingsListe = mutableListOf<BehandlingIdDto>()
        oppgaveKøRepository.lagreIkkeTaHensyn(it) { oppgaveKø ->
            checkNotNull(oppgaveKø) { "Fant ikke kø ved køoppdatering" }
            if (oppgaveKø.oppgaverOgDatoer == opprinnelige) {
                oppgaveKø.oppgaverOgDatoer = kø.oppgaverOgDatoer
            } else {
                oppgaveKø.oppgaverOgDatoer.clear()
                for (oppgave in aktiveOppgaver) {
                    if (kø.kode6 == oppgave.kode6) {
                        oppgaveKø.leggOppgaveTilEllerFjernFraKø(
                            oppgave = oppgave,
                            merknader = oppgaveRepositoryV2.hentMerknader(oppgave.eksternId.toString())
                        )
                    }
                }
            }
            behandlingsListe.addAll(
                oppgaveRepository.hentOppgaver(oppgaveKø.oppgaverOgDatoer.take(20).map { it.id })
                    .filter { it.system == Fagsystem.K9SAK.kode }.map { BehandlingIdDto(it.eksternId) }
            )
            oppgaveKø
        }
        oppgaveTjeneste.hentAntallOppgaver(oppgavekøId = it, taMedReserverte = true, refresh = true)
        oppgaveTjeneste.hentAntallOppgaver(oppgavekøId = it, taMedReserverte = false, refresh = true)
        k9SakService.refreshBehandlinger(BehandlingIdListe(behandlingsListe))
    }
}
