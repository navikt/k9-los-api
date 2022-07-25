package no.nav.k9.eventhandler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import no.nav.k9.domene.modell.Fagsystem
import no.nav.k9.domene.repository.OppgaveRepository
import no.nav.k9.integrasjon.k9.IK9SakService
import no.nav.k9.sak.kontrakt.behandling.BehandlingIdDto
import no.nav.k9.sak.kontrakt.behandling.BehandlingIdListe
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors


fun CoroutineScope.refreshK9(
    channel: ReceiveChannel<UUID>,
    k9SakService: IK9SakService,
    oppgaveRepository: OppgaveRepository
) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcherWithErrorHandling()) {
    val log = LoggerFactory.getLogger("refreshK9")

    val oppgaveListe = mutableListOf<UUID>()
    oppgaveListe.add(channel.receive())
    while (true) {
        val oppgaveId = channel.poll()
        if (oppgaveId == null) {
            try {
                refreshK9(oppgaveListe
                    .map { oppgaveRepository.hent(it) }
                    .filter { it.system == Fagsystem.K9SAK.kode }
                    .map { it.eksternId },
                    k9SakService
                )
                oppgaveListe.clear()
            } catch (e: Exception) {
                log.error("Feilet ved refresh av oppgaver i k9-sak", e)
            }
            oppgaveListe.add(channel.receive())
        } else {
            oppgaveListe.add(oppgaveId)
        }
    }
}

private suspend fun refreshK9(
    oppgaveListe: List<UUID>,
    k9SakService: IK9SakService
) {
    val behandlingsListe = mutableListOf<BehandlingIdDto>()
    behandlingsListe.addAll(oppgaveListe.map { BehandlingIdDto(it) })
    k9SakService.refreshBehandlinger(BehandlingIdListe(behandlingsListe))
}
