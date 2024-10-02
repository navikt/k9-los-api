package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.Configuration
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.Fagsystem
import no.nav.k9.los.integrasjon.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.OppgavetypeRepository
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.concurrent.timer

class OppgavestatistikkTjeneste(
    private val oppgavetypeRepository: OppgavetypeRepository,
    private val statistikkPublisher: StatistikkPublisher,
    private val transactionalManager: TransactionalManager,
    private val statistikkRepository: StatistikkRepository,
    private val config: Configuration,
    private val pepClient: IPepClient
) {

    private val log = LoggerFactory.getLogger(OppgavestatistikkTjeneste::class.java)
    private val TRÅDNAVN = "k9los-til-statistikk"

    fun kjør(kjørUmiddelbart: Boolean = false) {
        if (config.nyOppgavestyringDvhSendingAktivert()) {
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
        log.info("Schedulerer avspilling av statistikk til å kjøre 5 minutter fra nå, og hver time etter det")
        timer(
            name = TRÅDNAVN,
            daemon = true,
            initialDelay = TimeUnit.MINUTES.toMillis(5),
            period = TimeUnit.HOURS.toMillis(1)
        ) {
            if (LocalDateTime.now().isBefore(LocalDateTime.of(2023, 6, 9, 20, 0))) {
                log.info("Nullstiller datavarehussending")
                statistikkRepository.fjernSendtMarkering()
                log.info("Datavarehussending nullstilt")
            }
            try {
                spillAvStatistikk()
            } catch (e: Exception) {
                log.warn("Overføring av statistikk til DVH feilet. Retry om en time", e)
            }
        }
    }

    private fun spillAvStatistikk() {
        log.info("Starter sending av saks- og behandlingsstatistikk til DVH")
        val tidStatistikksendingStartet = System.currentTimeMillis()
        val oppgaverSomIkkeErSendt = statistikkRepository.hentOppgaverSomIkkeErSendt()
        log.info("Fant ${oppgaverSomIkkeErSendt.size} oppgaveversjoner som ikke er sendt til DVH")
        oppgaverSomIkkeErSendt.forEachIndexed { index, oppgaveId ->
            sendStatistikk(oppgaveId)
            if (index.mod(100) == 0) {
                log.info("Sendt $index eventer")
            }
        }
        val tidStatistikksendingFerdig = System.currentTimeMillis()
        val kjøretid = tidStatistikksendingFerdig - tidStatistikksendingStartet
        log.info("Sending av saks- og behanlingsstatistikk ferdig")
        log.info("Sendt ${oppgaverSomIkkeErSendt.size} oppgaversjoner. Totalt tidsbruk: ${kjøretid} ms")
        if (oppgaverSomIkkeErSendt.isNotEmpty()) {
            log.info("Gjennomsnitt tidsbruk: ${kjøretid / oppgaverSomIkkeErSendt.size} ms pr oppgaveversjon")
        }
    }

    @WithSpan
    private fun sendStatistikk(@SpanAttribute oppgaveId : Long){
        transactionalManager.transaction { tx ->
            sendStatistikk(oppgaveId, tx)
            statistikkRepository.kvitterSending(oppgaveId)
        }
    }

    private fun sendStatistikk(id: Long, tx: TransactionalSession) {
        var (sak, behandling) = byggOppgavestatistikk(id, tx)
        val erKode6 = runBlocking { pepClient.erSakKode6(sak.saksnummer) }
        if (erKode6) {
            sak = nullUtEventuelleSensitiveFelter(sak)
        }

        behandling.map {
            if (erKode6) {
                nullUtEventuelleSensitiveFelter(it)
            } else it
        }
            .onEach { log.info("Utgående DvhBehandling: "+ it.tryggToString()) }
            .forEach { statistikkPublisher.publiser(sak, it) }
    }

    private fun nullUtEventuelleSensitiveFelter(sak: Sak): Sak {
        return sak.copy(aktorer = sak.aktorer.map { Aktør(aktorId = -5, rolle = "-5", rolleBeskrivelse = "-5") })
    }

    private fun nullUtEventuelleSensitiveFelter(behandling: Behandling): Behandling {
        return behandling.copy(
            beslutter = "-5",
            saksbehandler = "-5",
            behandlingOpprettetAv = "-5",
            ansvarligEnhetKode = "-5"
        )
    }

    private fun byggOppgavestatistikk(oppgaveId: Long, tx: TransactionalSession): Pair<Sak, List<Behandling>> {
        val oppgave = statistikkRepository.hentOppgaveForId(tx, oppgaveId)

        return when (oppgave.oppgavetype.eksternId) {
            "k9sak" -> Pair(
                    K9SakOppgaveTilDVHMapper().lagSak(oppgave),
                    K9SakOppgaveTilDVHMapper().lagBehandlinger(oppgave)
                )
            "k9klage" -> Pair(
                    K9KlageOppgaveTilDVHMapper().lagSak(oppgave),
                    listOf(K9KlageOppgaveTilDVHMapper().lagBehandling(oppgave))
                )
            else -> throw IllegalStateException("Ukjent oppgavetype for sending til DVH: ${oppgave.oppgavetype.eksternId}")
        }
    }

    fun slettStatistikkgrunnlag(fagsystem: Fagsystem?) {
        statistikkRepository.fjernSendtMarkering(fagsystem)
    }
}