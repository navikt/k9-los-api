package no.nav.k9.nyoppgavestyring.domeneadaptere.statistikk

import kotliquery.TransactionalSession
import no.nav.k9.Configuration
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer

class OppgavestatistikkTjeneste(
    private val oppgaveRepository: OppgaveRepository,
    private val statistikkPublisher: StatistikkPublisher,
    private val transactionalManager: TransactionalManager,
    private val statistikkRepository: StatistikkRepository,
    private val config: Configuration
) {

    fun kjør() {
        if (config.nyOppgavestyringAktivert()) {
            fixedRateTimer(
                name = "k9los-til-statistikk",
                daemon = true,
                initialDelay = TimeUnit.DAYS.toMillis(1),
                period = TimeUnit.DAYS.toMillis(1)
            ) {
                spillAvStatistikk()
            }
        }
    }

    fun spillAvStatistikk() {
        log.info("Starter sending av saks- og behandlingsstatistikk til DVH")
        val tidStatistikksendingStartet = System.currentTimeMillis()
        val oppgaverSomIkkeErSendt = statistikkRepository.hentOppgaverSomIkkeErSendt()
        log.info("Fant ${oppgaverSomIkkeErSendt.size} oppgaveversjoner som ikke er sendt til DVH")
        oppgaverSomIkkeErSendt.forEachIndexed { index, oppgaveId ->
            transactionalManager.transaction { tx ->
                sendStatistikk(oppgaveId, tx)
                statistikkRepository.kvitterSending(oppgaveId)
            }
            if (index.mod(100) == 0) {
                log.info("Sendt $index eventer")
            }
        }
        val tidStatistikksendingFerdig = System.currentTimeMillis()
        val kjøretid = tidStatistikksendingFerdig - tidStatistikksendingStartet
        log.info("Sending av saks- og behanlingsstatistikk ferdig")
        log.info("Sendt ${oppgaverSomIkkeErSendt.size} oppgaversjoner. Totalt tidsbruk: ${kjøretid} ms")
        if (oppgaverSomIkkeErSendt.size > 0) {
            log.info("Gjennomsnitt tidsbruk: ${kjøretid/oppgaverSomIkkeErSendt.size} ms pr oppgaveversjon")
        }
    }

    private val log = LoggerFactory.getLogger(OppgavestatistikkTjeneste::class.java)

    private fun sendStatistikk(id: Long, tx: TransactionalSession) {
        val (sak, behandling) = byggOppgavestatistikk(id, tx)
        statistikkPublisher.publiser(sak, behandling)
    }

    private fun byggOppgavestatistikk(id: Long, tx: TransactionalSession): Pair<Sak, Behandling> {
        val oppgave = oppgaveRepository.hentOppgaveForId(tx, id)
        val behandling = OppgaveTilBehandlingMapper().lagBehandling(oppgave)
        val sak = OppgaveTilSakMapper().lagSak(oppgave)
        return Pair(sak, behandling)
    }

    fun slettStatistikkgrunnlag() {
        statistikkRepository.fjernSendtMarkering()
    }

}