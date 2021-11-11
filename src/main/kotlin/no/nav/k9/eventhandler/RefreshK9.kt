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
import java.util.*
import java.util.concurrent.Executors


fun CoroutineScope.refreshK9(
    channel: ReceiveChannel<UUID>,
    k9SakService: IK9SakService,
    oppgaveRepository: OppgaveRepository
) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcher()) {
    val oppgaveListe = mutableListOf<UUID>()
    oppgaveListe.add(channel.receive())
    while (true) {
        val oppgaveId = channel.poll()
        if (oppgaveId == null) {
            refreshK9(oppgaveListe, k9SakService)
            oppgaveListe.clear()
            oppgaveListe.add(channel.receive())
        } else {
            val oppgave = oppgaveRepository.hent(oppgaveId)
            if (oppgave.system == Fagsystem.K9SAK.kode) {
                oppgaveListe.add(oppgaveId)
            }
        }
    }
}

private suspend fun refreshK9(
    oppgaveListe: MutableList<UUID>,
    k9SakService: IK9SakService
) {
    val behandlingsListe = mutableListOf<BehandlingIdDto>()
    behandlingsListe.addAll(oppgaveListe.map { BehandlingIdDto(it) }.toList())
    k9SakService.refreshBehandlinger(BehandlingIdListe(behandlingsListe))
}
