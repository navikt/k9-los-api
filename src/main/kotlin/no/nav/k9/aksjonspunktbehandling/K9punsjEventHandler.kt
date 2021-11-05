package no.nav.k9.aksjonspunktbehandling

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.runBlocking
import no.nav.k9.domene.repository.*
import no.nav.k9.integrasjon.kafka.dto.PunsjEventDto
import no.nav.k9.tjenester.saksbehandler.oppgave.ReservasjonTjeneste
import org.slf4j.LoggerFactory


class K9punsjEventHandler constructor(
    private val oppgaveRepository: OppgaveRepository,
    private val punsjEventK9Repository: PunsjEventK9Repository,
    private val statistikkChannel: Channel<Boolean>,
    private val reservasjonRepository: ReservasjonRepository,
    private val oppgaveKøRepository: OppgaveKøRepository,
    private val reservasjonTjeneste: ReservasjonTjeneste
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
            reservasjonTjeneste.fjernReservasjon(oppgave)
        }

        runBlocking {
            for (oppgavekø in oppgaveKøRepository.hentKøIdIkkeTaHensyn()) {
                oppgaveKøRepository.leggTilOppgaverTilKø(oppgavekø, listOf(oppgave), reservasjonRepository)
            }
            statistikkChannel.send(true)
        }
    }
}
