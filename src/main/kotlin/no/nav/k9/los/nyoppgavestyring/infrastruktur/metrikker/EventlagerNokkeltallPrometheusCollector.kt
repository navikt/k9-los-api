package no.nav.k9.los.nyoppgavestyring.infrastruktur.metrikker

import io.prometheus.client.Collector
import io.prometheus.client.GaugeMetricFamily
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventRepository
import no.nav.k9.los.nyoppgavestyring.kodeverk.Fagsystem

class EventlagerNokkeltallPrometheusCollector(
    private val eventRepository: EventRepository,
    registerCollector: Boolean = true,
) : Collector() {

    init {
        if (registerCollector) {
            this.register<EventlagerNokkeltallPrometheusCollector>()
        }
    }

    override fun collect(): MutableList<MetricFamilySamples> {
        val dirtyPerFagsystem = eventRepository.hentAntallDirtyEventerPerFagsystem().associate { it.fagsystem to it.antall }
        val dirtyEventnoklerPerFagsystem = eventRepository.hentAntallDirtyEventnoklerPerFagsystem().associate { it.fagsystem to it.antall }
        val historikkvaskPerFagsystem = eventRepository.hentAntallHistorikkvaskbestillingerPerFagsystem().associate { it.fagsystem to it.antall }

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

        Fagsystem.entries.forEach { fagsystem ->
            val label = fagsystem.kode
            dirtyGauge.addMetric(listOf(label), (dirtyPerFagsystem[label] ?: 0L).toDouble())
            dirtyEventnokkelGauge.addMetric(listOf(label), (dirtyEventnoklerPerFagsystem[label] ?: 0L).toDouble())
            historikkvaskGauge.addMetric(listOf(label), (historikkvaskPerFagsystem[label] ?: 0L).toDouble())
        }

        return mutableListOf(dirtyGauge, dirtyEventnokkelGauge, historikkvaskGauge)
    }
}


