package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.toList
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Repository
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class K9sakBehandlingsoppfriskingJobb(
    val reservasjonRepository: ReservasjonV3Repository,
    val refreshK9v3Tjeneste: RefreshK9v3Tjeneste,
    val refreshOppgaveChannel: Channel<UUID>,

    //domenespesifikk konfigurasjon som tilpasses etter erfaringer i prod
    val antallFraHverKø: Int = 10,
    val ignorerReserverteOppgaverSomUtløperEtter: Duration = Duration.ofDays(1),
) {
    private val log = LoggerFactory.getLogger(K9sakBehandlingsoppfriskingJobb::class.java)

    suspend fun utfør() {
        log.info("Starter refresh av k9sak-behandlinger")
        val behandlingerTilRefresh = finnK9sakBehandlingerTilRefresh()
        channelSend(behandlingerTilRefresh)
    }

    private fun finnK9sakBehandlingerTilRefresh(): Set<UUID> {
        val reserverteOppgaver = hentK9sakReserverteBehandlinger(ignorerEtter = ignorerReserverteOppgaverSomUtløperEtter)
        val oppgaverFørstIKøer = refreshK9v3Tjeneste.behandlingerTilOppfriskning(antallFraHverKø)
        log.info("Fant ${oppgaverFørstIKøer.size} oppgaver først i køer, og ${reserverteOppgaver.size} reserverte oppgaver")
        return oppgaverFørstIKøer + reserverteOppgaver
    }

    private suspend fun channelSend(behandlingerTilRefresh: Set<UUID>) {
        for (uuid in behandlingerTilRefresh) {
            refreshOppgaveChannel.send(uuid)
        }
        log.info("Antall oppgaver i refreshOppgaveChannel er nå ${refreshOppgaveChannel.toList().size}")
    }

    private fun hentK9sakReserverteBehandlinger(ignorerEtter: Duration): Set<UUID> {
        val nå = LocalDateTime.now()
        return reservasjonRepository.hentOppgaverIdForAktiveReservasjonerForK9SakRefresh(
            gyldigPåTidspunkt = nå,
            utløperInnen = nå.plus(ignorerEtter)
        )
    }
}