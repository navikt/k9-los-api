package no.nav.k9.los.nyoppgavestyring.domeneadaptere.statistikk

import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.visningoguttrekk.OppgaveRepository
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.concurrent.fixedRateTimer
import kotlin.concurrent.thread

class OppgavestatistikkTjeneste(
    private val oppgaveRepository: OppgaveRepository,
    private val statistikkPublisher: StatistikkPublisher,
    private val transactionalManager: TransactionalManager,
    private val statistikkRepository: StatistikkRepository,
    private val config: Configuration,
    private val pepClient: IPepClient
) {

    private val log = LoggerFactory.getLogger(OppgavestatistikkTjeneste::class.java)
    private val TRÅDNAVN = "k9los-til-statistikk"

    fun kjør(kjørUmiddelbart: Boolean = false) {
        if (config.nyOppgavestyringAktivert()) {
            when (kjørUmiddelbart) {
                true -> spillAvUmiddelbart()
                false -> schedulerAvspilling()
            }
        } else log.info("Ny oppgavestyring er deaktivert")
    }

    private fun spillAvUmiddelbart() {
        log.info("Spiller av BehandlingProsessEventer umiddelbart")
        thread(
            start = true,
            isDaemon = true,
            name = TRÅDNAVN
        ) {
            spillAvStatistikk()
        }
    }

    private fun schedulerAvspilling() {
        log.info("Schedulerer avspilling av statistikk til å kjøre 1 dag fra nå, og hver 24. time etter det")
        fixedRateTimer(
            name = TRÅDNAVN,
            daemon = true,
            initialDelay = TimeUnit.DAYS.toMillis(1),
            period = TimeUnit.DAYS.toMillis(1)
        ) {
            spillAvStatistikk()
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
            log.info("Gjennomsnitt tidsbruk: ${kjøretid / oppgaverSomIkkeErSendt.size} ms pr oppgaveversjon")
        }
    }

    private fun sendStatistikk(id: Long, tx: TransactionalSession) {
        var (sak, behandling) = byggOppgavestatistikk(id, tx)
        val erKode6 = runBlocking { pepClient.erSakKode6(sak.saksnummer) }
        if (erKode6) {
            sak = nullUtEventuelleSensitiveFelter(sak)
            behandling = nullUtEventuelleSensitiveFelter(behandling)
        }
        statistikkPublisher.publiser(sak, behandling)
    }

    private fun nullUtEventuelleSensitiveFelter(sak: Sak): Sak {
        return sak.copy(aktorer = sak.aktorer.map { Aktør(aktorId = -5, rolle = "-5", rolleBeskrivelse = "-5") })
    }

    private fun nullUtEventuelleSensitiveFelter(behandling: Behandling): Behandling {
        return behandling.copy(beslutter = "-5", saksbehandler = "-5", behandlingOpprettetAv = "-5", ansvarligEnhetKode = "-5", behandlendeEnhetKode = "-5")
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