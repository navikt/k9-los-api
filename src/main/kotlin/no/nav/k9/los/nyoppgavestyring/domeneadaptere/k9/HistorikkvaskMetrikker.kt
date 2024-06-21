package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9

import io.prometheus.client.Histogram
import io.prometheus.client.SimpleTimer

class HistorikkvaskMetrikker {
    companion object {

        private val tidsforbruk = Histogram.build()
            .name("k9los_historikkvask")
            .help("Tidsforbruk historikkvask i k9los")
            .labelNames("navn")
            .register()


        fun observe(jobb: String, starttidNanos: Long) {
            tidsforbruk.labels(jobb).observe(SimpleTimer.elapsedSecondsFromNanos(starttidNanos, System.nanoTime()))
        }

    }
}