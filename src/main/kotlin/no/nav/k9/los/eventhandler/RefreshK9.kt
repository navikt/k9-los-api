package no.nav.k9.los.eventhandler

import io.opentelemetry.api.trace.Span
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.eventhandler.RefreshK9v3.Companion
import no.nav.k9.los.integrasjon.k9.IK9SakService
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.LoggerFactory
import java.util.*
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

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
                            oppfrisk(oppgaveListe)
                            oppgaveListe.clear()
                        }
                    } catch (e: Exception) {
                        log.error("Feilet ved refresh av ${oppgaveListe.size} oppgaver i k9-sak", e)
                        for (uuid in oppgaveListe) {
                            log.warn("Feilet ved refresh av $uuid i k9-sak");
                        }
                    } catch (t : Throwable) {
                        log.error("Feilet hardt (Throwable) ved refresh av ${oppgaveListe.size} oppgaver (v1) mot k9-sak, avslutter tråden", t)
                        throw t;
                    }
                    oppgaveListe.add(channel.receive())
                } else {
                    oppgaveListe.add(oppgaveId)
                }
            }
        }


    @WithSpan("refreshK9.oppfrisk")
    private suspend fun oppfrisk(oppgaveListe : List<UUID>) {
        refreshK9(
            oppgaveListe.fold(mutableSetOf()) { acc: MutableSet<UUID>, uuid: UUID ->
                acc.apply { addAll(hentTilhørendeOppgaverFraK9sak(uuid)) }
            }.toList()
        )
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
        withContext(coroutineContext + Span.current().asContextElement()) {
            k9SakService.refreshBehandlinger(oppgaveListe)
        }
    }
}