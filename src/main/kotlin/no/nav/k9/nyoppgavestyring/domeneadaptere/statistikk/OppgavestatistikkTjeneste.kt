package no.nav.k9.nyoppgavestyring.domeneadaptere.statistikk

import kotliquery.TransactionalSession
import no.nav.k9.Configuration
import no.nav.k9.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.thread

class OppgavestatistikkTjeneste(
    private val oppgaveRepository: OppgaveRepository,
    private val statistikkPublisher: StatistikkPublisher,
    private val transactionalManager: TransactionalManager,
    private val statistikkRepository: StatistikkRepository,
    private val config: Configuration
) {

    private val log = LoggerFactory.getLogger(OppgavestatistikkTjeneste::class.java)
    private val TRÅDNAVN = "k9los-til-statistikk"

    companion object {
        private var avspillingKjører = false
    }

    fun kjør(kjørUmiddelbart: Boolean = false) {
        if (config.nyOppgavestyringAktivert()) {
            if (!avspillingKjører) {
                when (kjørUmiddelbart) {
                    true -> spillAvUmiddelbart()
                    false -> schedulerAvspilling()
                }
            } else log.info("Avspilling av statistikk kjører allerede")
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    private fun spillAvUmiddelbart() {
        log.info("Spiller av BehandlingProsessEventer umiddelbart")
        avspillingKjører = true
        thread(
            start = true,
            isDaemon = true,
            name = TRÅDNAVN
        ) {
            spillAvStatistikk()
            avspillingKjører = false
        }
    }

    private fun schedulerAvspilling() {
        log.info("Schedulerer avspilling av statistikk til å kjøre 1 dag fra nå, og hver 24. time etter det")
        avspillingKjører = true
        fixedRateTimer(
            name = TRÅDNAVN,
            daemon = true,
            initialDelay = TimeUnit.DAYS.toMillis(1),
            period = TimeUnit.DAYS.toMillis(1)
        ) {
            spillAvStatistikk()
            avspillingKjører = false
        }
    }

    private fun spillAvStatistikk() {
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