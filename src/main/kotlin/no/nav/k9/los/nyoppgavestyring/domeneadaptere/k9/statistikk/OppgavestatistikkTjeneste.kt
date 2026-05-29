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

    private class PepCachePerSaksnummerState(private val maksAntallEksternIdPerSaksnummer: Int = 32) {
        private var gjeldendeSaksnummer: String? = null
        private val kode6PerOppgaveEksternId = object : LinkedHashMap<String, Boolean>(maksAntallEksternIdPerSaksnummer, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > maksAntallEksternIdPerSaksnummer
            }
        }

        fun hentEllerOppdater(saksnummer: String, oppgaveEksternId: String, lookup: () -> Boolean): Boolean {
            if (gjeldendeSaksnummer != saksnummer) {
                gjeldendeSaksnummer = saksnummer
                kode6PerOppgaveEksternId.clear()
            }

            return kode6PerOppgaveEksternId.getOrPut(oppgaveEksternId, lookup)
        }
    }

    private data class Oppgavestatistikkgrunnlag(
        val sak: Sak,
        val behandlinger: List<Behandling>,
        val oppgaveEksternId: String,
    )

    private val log = LoggerFactory.getLogger(OppgavestatistikkTjeneste::class.java)
    private val k9SakMapper = K9SakOppgaveTilDVHMapper()
    private val k9KlageMapper = K9KlageOppgaveTilDVHMapper()

    fun spillAvUsendtStatistikk() {
        log.info("Starter sending av saks- og behandlingsstatistikk til DVH")
        val tidStatistikksendingStartet = System.currentTimeMillis()
        val oppgaverSomIkkeErSendt = statistikkRepository.hentOppgaverSomIkkeErSendt()
        val pepCacheState = PepCachePerSaksnummerState()
        log.info("Fant ${oppgaverSomIkkeErSendt.size} oppgaveversjoner som ikke er sendt til DVH")
        oppgaverSomIkkeErSendt.forEachIndexed { index, oppgaveId ->
            sendStatistikk(oppgaveId, pepCacheState)
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
    private fun sendStatistikk(
        @SpanAttribute oppgaveId: Long,
        pepCacheState: PepCachePerSaksnummerState,
    ) {
        transactionalManager.transaction { tx ->
            sendStatistikk(oppgaveId, tx, pepCacheState)
            statistikkRepository.kvitterSending(tx, oppgaveId)
        }
    }

    private fun sendStatistikk(id: Long, tx: TransactionalSession, pepCacheState: PepCachePerSaksnummerState) {
        val oppgavestatistikkgrunnlag = byggOppgavestatistikk(id, tx)
        val erKode6 = pepCacheState.hentEllerOppdater(
            saksnummer = oppgavestatistikkgrunnlag.sak.saksnummer,
            oppgaveEksternId = oppgavestatistikkgrunnlag.oppgaveEksternId,
        ) {
            pepCacheRepository.hent("K9", oppgavestatistikkgrunnlag.oppgaveEksternId, tx)?.kode6 ?: false
        }

        val sakTilSending = if (erKode6) nullUtEventuelleSensitiveFelter(oppgavestatistikkgrunnlag.sak) else oppgavestatistikkgrunnlag.sak
        oppgavestatistikkgrunnlag.behandlinger.forEach {
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

    private fun byggOppgavestatistikk(oppgaveId: Long, tx: TransactionalSession): Oppgavestatistikkgrunnlag {
        val (oppgave, versjon) = statistikkRepository.hentOppgaveForId(tx, oppgaveId)

        return when (oppgave.oppgavetype.eksternId) {
            "k9sak" -> Oppgavestatistikkgrunnlag(
                sak = k9SakMapper.lagSak(oppgave),
                behandlinger = k9SakMapper.lagBehandlinger(oppgave, versjon),
                oppgaveEksternId = oppgave.eksternId,
            )
            "k9klage" -> Oppgavestatistikkgrunnlag(
                sak = k9KlageMapper.lagSak(oppgave),
                behandlinger = listOf(k9KlageMapper.lagBehandling(oppgave)),
                oppgaveEksternId = oppgave.eksternId,
            )
            else -> throw IllegalStateException("Ukjent oppgavetype for sending til DVH: ${oppgave.oppgavetype.eksternId}")
        }
    }

    fun slettStatistikkgrunnlag(oppgavetype: String) {
        statistikkRepository.fjernSendtMarkering(oppgavetype)
    }
}