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

    fun kjør(kjørSetup: Boolean) {
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

    public fun spillAvStatistikk() {
        // Hente oppgaveversjoner som ikke finnes i OPPGAVE_V3_SENDT_DVH (som ikke er sendt)
        val oppgaverSomIkkeErSendt = statistikkRepository.hentOppgaverSomIkkeErSendt()

        oppgaverSomIkkeErSendt.forEach { oppgaveId ->
            transactionalManager.transaction { tx ->
                sendStatistikk(oppgaveId, tx)
                statistikkRepository.kvitterSending(oppgaveId)
            }
        }
    }

    private val log = LoggerFactory.getLogger(OppgavestatistikkTjeneste::class.java)

    private fun sendStatistikk(id: Long, tx: TransactionalSession) {
        val (sak, behandling) = byggOppgavestatistikk(id, tx)
        statistikkPublisher.publiser(sak, behandling)
    }

    //TODO: koble fra transaksjon? Dette burde ikke kjøre i samme tx som lagring av oppgave, tror jeg
    private fun byggOppgavestatistikk(id: Long, tx: TransactionalSession): Pair<Sak, Behandling> {
        val hentOppgave = System.currentTimeMillis()
        val oppgave = oppgaveRepository.hentOppgaveForId(tx, id)
        log.info("Hentet oppgave med historiske versjoner: $id, tidsbruk: ${System.currentTimeMillis() - hentOppgave}")
        val behandling = OppgaveTilBehandlingMapper().lagBehandling(oppgave)
        val sak = OppgaveTilSakMapper().lagSak(oppgave)
        return Pair(sak, behandling)
    }

}