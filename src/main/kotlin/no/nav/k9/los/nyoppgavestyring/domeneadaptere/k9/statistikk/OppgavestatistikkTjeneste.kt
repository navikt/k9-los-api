package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.statistikk

import io.opentelemetry.instrumentation.annotations.SpanAttribute
import io.opentelemetry.instrumentation.annotations.WithSpan
import kotliquery.TransactionalSession
import no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache.PepCacheRepository
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import org.slf4j.LoggerFactory

class OppgavestatistikkTjeneste(
    private val statistikkPublisher: StatistikkPublisher,
    private val transactionalManager: TransactionalManager,
    private val statistikkRepository: StatistikkRepository,
    private val pepCacheRepository: PepCacheRepository
) {

    private val log = LoggerFactory.getLogger(OppgavestatistikkTjeneste::class.java)
    private val k9SakMapper = K9SakOppgaveTilDVHMapper()
    private val k9KlageMapper = K9KlageOppgaveTilDVHMapper()

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
    private fun sendStatistikk(@SpanAttribute oppgaveId: Long) {
        transactionalManager.transaction { tx ->
            sendStatistikk(oppgaveId, tx)
            statistikkRepository.kvitterSending(tx, oppgaveId)
        }
    }

    private fun sendStatistikk(id: Long, tx: TransactionalSession) {
        val (sak, behandling, oppgaveEksternId) = byggOppgavestatistikk(id, tx)
        val erKode6 = pepCacheRepository.hent("K9", oppgaveEksternId, tx)?.kode6 ?: false

        val sakTilSending = if (erKode6) nullUtEventuelleSensitiveFelter(sak) else sak
        behandling.forEach {
            val behandlingTilSending = if (erKode6) nullUtEventuelleSensitiveFelter(it) else it
            if (log.isDebugEnabled) {
                log.debug("Utgående DvhBehandling: {}", behandlingTilSending.tryggToString())
            }
            statistikkPublisher.publiser(sakTilSending, behandlingTilSending)
        }
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

    private fun byggOppgavestatistikk(oppgaveId: Long, tx: TransactionalSession): Triple<Sak, List<Behandling>, String> {
        val (oppgave, versjon) = statistikkRepository.hentOppgaveForId(tx, oppgaveId)

        return when (oppgave.oppgavetype.eksternId) {
            "k9sak" -> Triple(
                k9SakMapper.lagSak(oppgave),
                k9SakMapper.lagBehandlinger(oppgave, versjon),
                oppgave.eksternId
            )
            "k9klage" -> Triple(
                k9KlageMapper.lagSak(oppgave),
                listOf(k9KlageMapper.lagBehandling(oppgave)),
                oppgave.eksternId
            )
            else -> throw IllegalStateException("Ukjent oppgavetype for sending til DVH: ${oppgave.oppgavetype.eksternId}")
        }
    }

    fun slettStatistikkgrunnlag(oppgavetype: String) {
        statistikkRepository.fjernSendtMarkering(oppgavetype)
    }
}