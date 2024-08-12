package no.nav.k9.los.eventhandler

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.integrasjon.k9.IK9SakService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors

class RefreshK9(
    val k9SakService: IK9SakService,
    val oppgaveRepository: OppgaveRepository,
    val transactionalManager: TransactionalManager
) {

    fun CoroutineScope.start(channel: Channel<UUID>) =
        launch(Executors.newSingleThreadExecutor().asCoroutineDispatcherWithErrorHandling()) {
            val log = LoggerFactory.getLogger("refreshK9")

            val oppgaveListe = mutableListOf<UUID>()
            oppgaveListe.add(channel.receive())
            while (true) {
                val oppgaveId = channel.tryReceive().getOrNull()
                if (oppgaveId == null) {
                    try {
                        ChannelMetrikker.timeSuspended("refresh_k9sak") {
                            refreshK9(
                                oppgaveListe.fold(mutableSetOf()) { acc: MutableSet<UUID>, uuid: UUID ->
                                    acc.apply { addAll(hentTilhørendeOppgaverFraK9sak(uuid)) }
                                }.toList()
                            )
                            oppgaveListe.clear()
                        }
                    } catch (e: Exception) {
                        log.error("Feilet ved refresh av oppgaver i k9-sak: " + oppgaveListe.joinToString(", "), e)
                    }
                    oppgaveListe.add(channel.receive())
                } else {
                    oppgaveListe.add(oppgaveId)
                }
            }
        }

    private fun hentTilhørendeOppgaverFraK9sak(eksternId: UUID): Set<UUID> {
        return transactionalManager.transaction { tx ->
            val oppgave = oppgaveRepository.hentNyesteOppgaveForEksternIdHvisFinnes(tx, "K9", eksternId.toString())
            if (oppgave == null) {
                emptySet()
            } else if (oppgave.oppgavetype.eksternId != "k9sak") {
                emptySet()
            } else {
                 oppgaveRepository.hentAlleÅpneOppgaverForReservasjonsnøkkel(tx, oppgave.reservasjonsnøkkel)
                     .map { UUID.fromString(it.eksternId) }
                     .toSet()
            }
        }
    }

    private suspend fun refreshK9(oppgaveListe: List<UUID>) {
        k9SakService.refreshBehandlinger(oppgaveListe)
    }
}