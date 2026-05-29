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

    private class PepCachePerSaksnummerState(
        private val pepCacheRepository: PepCacheRepository,
        private val maksAntallEksternIdPerSaksnummer: Int = 32,
    ) {
        private var gjeldendeSaksnummer: String? = null
        private val kode6PerOppgaveEksternId = object : LinkedHashMap<String, Boolean>(maksAntallEksternIdPerSaksnummer, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
                return size > maksAntallEksternIdPerSaksnummer
            }
        }

        fun hentEllerOppdater(saksnummer: String, oppgaveEksternId: String, tx: TransactionalSession): Boolean {
            if (gjeldendeSaksnummer != saksnummer) {
                gjeldendeSaksnummer = saksnummer
                kode6PerOppgaveEksternId.clear()
            }

            return kode6PerOppgaveEksternId.getOrPut(oppgaveEksternId) {
                pepCacheRepository.hent("K9", oppgaveEksternId, tx)?.kode6 ?: false
            }
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

    private class TimingStats(val name: String) {
        private val samples = mutableListOf<Long>()

        fun record(nanos: Long) { samples.add(nanos) }

        fun summary(): String {
            if (samples.isEmpty()) return "$name: ingen målinger"
            val sortert = samples.sorted()
            val min = sortert.first() / 1_000_000
            val max = sortert.last() / 1_000_000
            val avg = (sortert.sum() / sortert.size) / 1_000_000
            val p50 = sortert[sortert.size / 2] / 1_000_000
            val p95 = sortert[(sortert.size * 0.95).toInt().coerceAtMost(sortert.size - 1)] / 1_000_000
            return "$name: min=${min}ms avg=${avg}ms p50=${p50}ms p95=${p95}ms max=${max}ms (n=${sortert.size})"
        }

        fun reset() { samples.clear() }
    }

    companion object {
        private const val FLUSH_INTERVAL = 100
    }

    fun spillAvUsendtStatistikk() {
        log.info("Starter sending av saks- og behandlingsstatistikk til DVH")
        val tidStatistikksendingStartet = System.currentTimeMillis()
        val oppgaverSomIkkeErSendt = statistikkRepository.hentOppgaverSomIkkeErSendt()
        val pepCacheState = PepCachePerSaksnummerState(pepCacheRepository)
        log.info("Fant ${oppgaverSomIkkeErSendt.size} oppgaveversjoner som ikke er sendt til DVH")

        val dbFetchStats = TimingStats("db-fetch")
        val pepCacheStats = TimingStats("pep-cache")
        val kvitteringStats = TimingStats("kvittering")
        val totalPerOppgaveStats = TimingStats("total-per-oppgave")
        val kafkaFlushStats = TimingStats("kafka-flush")

        oppgaverSomIkkeErSendt.forEachIndexed { index, oppgaveId ->
            val oppgaveStart = System.nanoTime()
            sendStatistikkAsynkront(oppgaveId, pepCacheState, dbFetchStats, pepCacheStats, kvitteringStats)
            totalPerOppgaveStats.record(System.nanoTime() - oppgaveStart)

            if ((index + 1).mod(FLUSH_INTERVAL) == 0) {
                val flushStart = System.nanoTime()
                statistikkPublisher.flushOgValider()
                kafkaFlushStats.record(System.nanoTime() - flushStart)

                log.info("Sendt ${index + 1} eventer. Timing siste $FLUSH_INTERVAL: ${dbFetchStats.summary()}, ${pepCacheStats.summary()}, ${kvitteringStats.summary()}, ${totalPerOppgaveStats.summary()}, ${kafkaFlushStats.summary()}")
                dbFetchStats.reset()
                pepCacheStats.reset()
                kvitteringStats.reset()
                totalPerOppgaveStats.reset()
                kafkaFlushStats.reset()
            }
        }
        // Flush og logg resterende
        val flushStart = System.nanoTime()
        statistikkPublisher.flushOgValider()
        kafkaFlushStats.record(System.nanoTime() - flushStart)
        if (oppgaverSomIkkeErSendt.size.mod(FLUSH_INTERVAL) != 0) {
            log.info("Siste batch timing: ${dbFetchStats.summary()}, ${pepCacheStats.summary()}, ${kvitteringStats.summary()}, ${totalPerOppgaveStats.summary()}, ${kafkaFlushStats.summary()}")
        }

        val kjoretid = System.currentTimeMillis() - tidStatistikksendingStartet
        log.info("Sending av saks- og behandlingsstatistikk ferdig")
        log.info("Sendt ${oppgaverSomIkkeErSendt.size} oppgaveversjoner. Totalt tidsbruk: ${kjoretid} ms")
        if (oppgaverSomIkkeErSendt.isNotEmpty()) {
            log.info("Gjennomsnitt tidsbruk: ${kjoretid / oppgaverSomIkkeErSendt.size} ms pr oppgaveversjon")
        }
    }

    @WithSpan
    private fun sendStatistikkAsynkront(
        @SpanAttribute oppgaveId: Long,
        pepCacheState: PepCachePerSaksnummerState,
        dbFetchStats: TimingStats,
        pepCacheStats: TimingStats,
        kvitteringStats: TimingStats,
    ) {
        transactionalManager.transaction { tx ->
            val dbStart = System.nanoTime()
            val oppgavestatistikkgrunnlag = byggOppgavestatistikk(oppgaveId, tx)
            dbFetchStats.record(System.nanoTime() - dbStart)

            val pepStart = System.nanoTime()
            val erKode6 = pepCacheState.hentEllerOppdater(
                saksnummer = oppgavestatistikkgrunnlag.sak.saksnummer,
                oppgaveEksternId = oppgavestatistikkgrunnlag.oppgaveEksternId,
                tx = tx,
            )
            pepCacheStats.record(System.nanoTime() - pepStart)

            val sakTilSending = if (erKode6) nullUtEventuelleSensitiveFelter(oppgavestatistikkgrunnlag.sak) else oppgavestatistikkgrunnlag.sak
            oppgavestatistikkgrunnlag.behandlinger.forEach {
                val behandlingTilSending = if (erKode6) nullUtEventuelleSensitiveFelter(it) else it
                if (log.isDebugEnabled) {
                    log.debug("Utgående DvhBehandling: {}", behandlingTilSending.tryggToString())
                }
                statistikkPublisher.publiserAsynkront(sakTilSending, behandlingTilSending)
            }

            val kvitteringStart = System.nanoTime()
            statistikkRepository.kvitterSending(tx, oppgaveId)
            kvitteringStats.record(System.nanoTime() - kvitteringStart)
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