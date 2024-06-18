package no.nav.k9.los.eventhandler

import io.prometheus.client.Histogram
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.domene.repository.OppgaveRepository
import no.nav.k9.los.integrasjon.k9.IK9SakService
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors

private val tidsforbrukMetrikk = Histogram.build()
    .name("los_refreshk9")
    .help("Tidsforbruk refreshK9")
    .register()

fun CoroutineScope.refreshK9(
    channel: ReceiveChannel<UUID>,
    k9SakService: IK9SakService,
    oppgaveRepository: OppgaveRepository
) = launch(Executors.newSingleThreadExecutor().asCoroutineDispatcherWithErrorHandling()) {
    val log = LoggerFactory.getLogger("refreshK9")

    val oppgaveListe = mutableListOf<UUID>()
    oppgaveListe.add(channel.receive())
    while (true) {
        val oppgaveId = channel.tryReceive().getOrNull()
        if (oppgaveId == null) {
            try {
                val t0 = System.currentTimeMillis()
                refreshK9(oppgaveListe
                    .map { oppgaveRepository.hent(it) }
                    .filter { it.system == Fagsystem.K9SAK.kode }
                    .map { it.eksternId },
                    k9SakService
                )
                tidsforbrukMetrikk.observe((System.currentTimeMillis() - t0).toDouble())
                oppgaveListe.clear()
            } catch (e: Exception) {
                log.error("Feilet ved refresh av oppgaver i k9-sak: "+oppgaveListe.joinToString(", "), e)
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
    k9SakService.refreshBehandlinger(oppgaveListe)
}
