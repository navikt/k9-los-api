package no.nav.k9.los.eventhandler

import io.prometheus.client.Histogram
import io.prometheus.client.SimpleTimer
import kotlin.math.pow

class ChannelMetrikker {
    companion object {

        private val tidsforbruk = Histogram.build()
            .name("k9los_channel")
            .help("Tidsforbruk prosessering av channels i k9los")
            .labelNames("channel", "resultat")
            .exponentialBuckets(0.01, 10.0.pow(1.0 / 3.0), 12 )
            .register()

        fun <T> time(channel: String, operasjon: (() -> T)): T {
            val t0 = System.nanoTime()
            var status = "OK"
            try {
                val resultat = operasjon.invoke()
                return resultat
            } catch (e: Exception) {
                status = e.javaClass.simpleName
                throw e
            } finally {
                tidsforbruk.labels(channel, status).observe(SimpleTimer.elapsedSecondsFromNanos(t0, System.nanoTime()))
            }
        }

        suspend fun <T> timeSuspended(channel: String, operasjon: (suspend () -> T)): T {
            val t0 = System.nanoTime()
            var status = "OK"
            try {
                return operasjon.invoke()
            } catch (e: Exception) {
                status = e.javaClass.simpleName
                throw e
            } finally {
                tidsforbruk.labels(channel, status).observe(SimpleTimer.elapsedSecondsFromNanos(t0, System.nanoTime()))
            }
        }
    }
}