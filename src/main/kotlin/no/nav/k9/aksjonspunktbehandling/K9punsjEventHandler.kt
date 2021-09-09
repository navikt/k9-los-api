package no.nav.k9.aksjonspunktbehandling

import io.ktor.util.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.domene.lager.oppgave.Oppgave
import no.nav.k9.domene.repository.*
import no.nav.k9.integrasjon.kafka.dto.PunsjEventDto
import org.slf4j.LoggerFactory


class K9punsjEventHandler constructor(
    val oppgaveRepository: OppgaveRepository,
    val punsjEventK9Repository: PunsjEventK9Repository,
    val statistikkRepository: StatistikkRepository,
    val statistikkChannel: Channel<Boolean>,
    val reservasjonRepository: ReservasjonRepository,
    val oppgaveKøRepository: OppgaveKøRepository,
    val saksbehandlerRepository: SaksbehandlerRepository
) {
    private val log = LoggerFactory.getLogger(K9punsjEventHandler::class.java)

    fun prosesser(
        event: PunsjEventDto
    ) {
        log.info(event.toString())
        val modell = punsjEventK9Repository.lagre(event = event)
        val oppgave = modell.oppgave()
        oppgaveRepository.lagre(oppgave.eksternId){
            oppgave
        }

        if (modell.fikkEndretAksjonspunkt()) {
            fjernReservasjon(oppgave)
        }
        
        runBlocking {
            for (oppgavekø in oppgaveKøRepository.hentKøIdIkkeTaHensyn()) {
                oppgaveKøRepository.leggTilOppgaverTilKø(oppgavekø, listOf(oppgave), reservasjonRepository)
            }
            statistikkChannel.send(true)
        }
    }
    
    private fun fjernReservasjon(oppgave: Oppgave) {
        if (reservasjonRepository.finnes(oppgave.eksternId)) {
            reservasjonRepository.lagre(oppgave.eksternId) { reservasjon ->
                reservasjon!!.reservertTil = null
                reservasjon
            }
            val reservasjon = reservasjonRepository.hent(oppgave.eksternId)
            saksbehandlerRepository.fjernReservasjonIkkeTaHensyn(
                reservasjon.reservertAv,
                reservasjon.oppgave
            )
        }
    }
}
