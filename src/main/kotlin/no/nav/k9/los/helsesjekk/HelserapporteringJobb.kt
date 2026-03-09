package no.nav.k9.los.helsesjekk

import io.prometheus.client.Gauge

class HelserapporteringJobb(
    private val app: String,
    private val helsetjeneste: Helsetjeneste,
) {
    private companion object {
        private const val FRISK = 0.0
        private const val UFRISK = 1.0

        private val gauge = Gauge
            .build("health_check_status", "Indikerer applikasjonens helse status. 0 er OK, 1 indikerer feil.")
            .labelNames("app")
            .register()
    }

    suspend fun sjekkOgRapporter() {
        val resultater = helsetjeneste.sjekk()
        if (resultater.any { it is UnHealthy }) {
            gauge.labels(app).set(UFRISK)
        } else {
            gauge.labels(app).set(FRISK)
        }
    }
}
