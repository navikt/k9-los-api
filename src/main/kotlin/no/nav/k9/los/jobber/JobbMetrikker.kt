package no.nav.k9.los.jobber

import io.prometheus.client.Histogram
import io.prometheus.client.SimpleTimer
import kotlinx.coroutines.CoroutineScope
import kotlin.math.pow

class JobbMetrikker {
    companion object {

        private val tidsforbruk = Histogram.build()
            .name("k9los_jobb")
            .help("Tidsforbruk jobber i k9los")
            .labelNames("jobbnavn", "resultat")
            .exponentialBuckets(0.01, 10.0.pow(1.0 / 3.0), 12 )
            .register()


        fun observe(jobb: String, starttidNanos: Long,status : String = "OK") {
            tidsforbruk.labels(jobb, status).observe(SimpleTimer.elapsedSecondsFromNanos(starttidNanos, System.nanoTime()))
        }

        fun <T> time(jobb: String, operasjon: (() -> T)): T {
            val t0 = System.nanoTime()
            var status = "OK"
            try {
                val resultat = operasjon.invoke()
                return resultat
            } catch (e : Exception){
                status = e.javaClass.simpleName
                throw e
            } finally {
                tidsforbruk.labels(jobb, status).observe(SimpleTimer.elapsedSecondsFromNanos(t0, System.nanoTime()))
            }
        }
    }
}