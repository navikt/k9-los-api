package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.runBlocking
import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.IPepClient
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import org.slf4j.LoggerFactory

class OppgavestatistikkTjeneste(
    private val statistikkPublisher: StatistikkPublisher,
    private val transactionalManager: TransactionalManager,
    private val statistikkRepository: StatistikkRepository,
    private val pepClient: IPepClient
) {

    private val log = LoggerFactory.getLogger(OppgavestatistikkTjeneste::class.java)

    fun spillAvUsendtStatistikk() {
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
        val (oppgave, versjon) = statistikkRepository.hentOppgaveForId(tx, oppgaveId)

        return when (oppgave.oppgavetype.eksternId) {
            "k9sak" -> Pair(
                    K9SakOppgaveTilDVHMapper().lagSak(oppgave),
                    K9SakOppgaveTilDVHMapper().lagBehandlinger(oppgave, versjon)
                )
            "k9klage" -> Pair(
                    K9KlageOppgaveTilDVHMapper().lagSak(oppgave),
                    listOf(K9KlageOppgaveTilDVHMapper().lagBehandling(oppgave))
                )
            else -> throw IllegalStateException("Ukjent oppgavetype for sending til DVH: ${oppgave.oppgavetype.eksternId}")
        }
    }

    fun slettStatistikkgrunnlag(oppgavetype: String) {
        statistikkRepository.fjernSendtMarkering(oppgavetype)
    }
}