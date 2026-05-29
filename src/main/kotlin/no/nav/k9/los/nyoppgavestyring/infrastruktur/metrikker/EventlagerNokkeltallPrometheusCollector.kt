package no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker

import io.prometheus.client.Collector
import io.prometheus.client.GaugeMetricFamily
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem

class EventlagerNokkeltallPrometheusCollector(
    private val nokkeltallRepository: EventlagerNokkeltallRepository,
    registerCollector: Boolean = true,
) : Collector() {

    private data class CachedUsendtOppgavestatistikk(
        val hentetTidspunktMs: Long,
        val verdier: Map<String, Long>
    )

    private val usendtOppgavestatistikkCacheTtlMs = 60_000L
    private val usendtOppgavestatistikkLock = Any()

    @Volatile
    private var cachedUsendtOppgavestatistikk: CachedUsendtOppgavestatistikk? = null

    init {
        if (registerCollector) {
            this.register<EventlagerNokkeltallPrometheusCollector>()
        }
    }

    override fun collect(): MutableList<MetricFamilySamples> {
        val dirtyPerFagsystem = nokkeltallRepository.hentAntallDirtyEventerPerFagsystem().associate { it.fagsystem to it.antall }
        val dirtyEventnoklerPerFagsystem = nokkeltallRepository.hentAntallDirtyEventnoklerPerFagsystem().associate { it.fagsystem to it.antall }
        val historikkvaskPerFagsystem = nokkeltallRepository.hentAntallHistorikkvaskbestillingerPerFagsystem().associate { it.fagsystem to it.antall }
        val usendtOppgavestatistikkPerFagsystem = hentUsendtOppgavestatistikkPerFagsystemCached()

        val dirtyGauge = GaugeMetricFamily(
            "k9los_eventlager_dirty_eventer",
            "Antall dirty eventer i eventlager gruppert per fagsystem.",
            listOf("fagsystem")
        )

        val dirtyEventnokkelGauge = GaugeMetricFamily(
            "k9los_eventlager_dirty_eventnokler",
            "Antall event_nokkel med minst ett dirty event i eventlager gruppert per fagsystem.",
            listOf("fagsystem")
        )

        val historikkvaskGauge = GaugeMetricFamily(
            "k9los_eventlager_historikkvask_bestillinger",
            "Antall ubehandlede historikkvaskbestillinger gruppert per fagsystem.",
            listOf("fagsystem")
        )

        val usendtOppgavestatistikkGauge = GaugeMetricFamily(
            "k9los_eventlager_oppgavestatistikk_usendt",
            "Antall oppgaveversjoner som ikke er sendt til DVH gruppert per fagsystem.",
            listOf("fagsystem")
        )

        Fagsystem.entries.forEach { fagsystem ->
            val label = fagsystem.kode
            dirtyGauge.addMetric(listOf(label), (dirtyPerFagsystem[label] ?: 0L).toDouble())
            dirtyEventnokkelGauge.addMetric(listOf(label), (dirtyEventnoklerPerFagsystem[label] ?: 0L).toDouble())
            historikkvaskGauge.addMetric(listOf(label), (historikkvaskPerFagsystem[label] ?: 0L).toDouble())
            usendtOppgavestatistikkGauge.addMetric(listOf(label), (usendtOppgavestatistikkPerFagsystem[label] ?: 0L).toDouble())
        }

        return mutableListOf(dirtyGauge, dirtyEventnokkelGauge, historikkvaskGauge, usendtOppgavestatistikkGauge)
    }

    private fun hentUsendtOppgavestatistikkPerFagsystemCached(): Map<String, Long> {
        val now = System.currentTimeMillis()
        cachedUsendtOppgavestatistikk?.let {
            if (now - it.hentetTidspunktMs < usendtOppgavestatistikkCacheTtlMs) {
                return it.verdier
            }
        }

        synchronized(usendtOppgavestatistikkLock) {
            val cacheEtterLock = cachedUsendtOppgavestatistikk
            if (cacheEtterLock != null && now - cacheEtterLock.hentetTidspunktMs < usendtOppgavestatistikkCacheTtlMs) {
                return cacheEtterLock.verdier
            }

            val oppdatert = nokkeltallRepository.hentUsendtOppgavestatistikkPerOppgavetype()
                .associate { it.oppgavetypeEksternId.uppercase() to it.antall }
            cachedUsendtOppgavestatistikk = CachedUsendtOppgavestatistikk(now, oppdatert)
            return oppdatert
        }
    }
}
