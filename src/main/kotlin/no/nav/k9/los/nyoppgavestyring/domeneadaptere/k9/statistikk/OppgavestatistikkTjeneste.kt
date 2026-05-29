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

    private data class Kode6State(
        var saksnummer: String? = null,
        var erKode6: Boolean? = null
    )

    private val log = LoggerFactory.getLogger(OppgavestatistikkTjeneste::class.java)
    private val k9SakMapper = K9SakOppgaveTilDVHMapper()
    private val k9KlageMapper = K9KlageOppgaveTilDVHMapper()

    fun spillAvUsendtStatistikk() {
        log.info("Starter sending av saks- og behandlingsstatistikk til DVH")
        val tidStatistikksendingStartet = System.currentTimeMillis()
        val kode6State = Kode6State()
        val oppgaverSomIkkeErSendt = statistikkRepository.hentOppgaverSomIkkeErSendt()
        log.info("Fant ${oppgaverSomIkkeErSendt.size} oppgaveversjoner som ikke er sendt til DVH")
        oppgaverSomIkkeErSendt.forEachIndexed { index, oppgaveId ->
            sendStatistikk(oppgaveId, kode6State)
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
    private fun sendStatistikk(@SpanAttribute oppgaveId : Long, kode6State: Kode6State) {
        transactionalManager.transaction { tx ->
            sendStatistikk(oppgaveId, tx, kode6State)
            statistikkRepository.kvitterSending(tx, oppgaveId)
        }
    }

    private fun sendStatistikk(id: Long, tx: TransactionalSession, kode6State: Kode6State) {
        var (sak, behandling) = byggOppgavestatistikk(id, tx)
        val erKode6 = if (kode6State.saksnummer == sak.saksnummer && kode6State.erKode6 != null) {
            kode6State.erKode6!!
        } else {
            runBlocking { pepClient.erSakKode6(sak.saksnummer) }.also {
                kode6State.saksnummer = sak.saksnummer
                kode6State.erKode6 = it
            }
        }
        if (erKode6) {
            sak = nullUtEventuelleSensitiveFelter(sak)
        }

        behandling.forEach {
            val behandlingTilSending = if (erKode6) nullUtEventuelleSensitiveFelter(it) else it
            if (log.isDebugEnabled) {
                log.debug("Utgående DvhBehandling: {}", behandlingTilSending.tryggToString())
            }
            statistikkPublisher.publiser(sak, behandlingTilSending)
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

    private fun byggOppgavestatistikk(oppgaveId: Long, tx: TransactionalSession): Pair<Sak, List<Behandling>> {
        val (oppgave, versjon) = statistikkRepository.hentOppgaveForId(tx, oppgaveId)

        return when (oppgave.oppgavetype.eksternId) {
            "k9sak" -> Pair(
                    k9SakMapper.lagSak(oppgave),
                    k9SakMapper.lagBehandlinger(oppgave, versjon)
                )
            "k9klage" -> Pair(
                    k9KlageMapper.lagSak(oppgave),
                    listOf(k9KlageMapper.lagBehandling(oppgave))
                )
            else -> throw IllegalStateException("Ukjent oppgavetype for sending til DVH: ${oppgave.oppgavetype.eksternId}")
        }
    }

    fun slettStatistikkgrunnlag(oppgavetype: String) {
        statistikkRepository.fjernSendtMarkering(oppgavetype)
    }
}