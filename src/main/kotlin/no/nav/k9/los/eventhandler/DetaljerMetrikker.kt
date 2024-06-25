package no.nav.k9.los.eventhandler

import io.prometheus.client.Histogram
import io.prometheus.client.SimpleTimer

object DetaljerMetrikker {

    private val tidsforbruk = Histogram.build()
        .name("k9los_internal")
        .help("Tidsforbruk diverse internt")
        .labelNames("label1", "label2", "resultat")
        .exponentialBuckets(0.01, Math.pow(10.0, 1.0 / 3.0), 12)
        .register()

    fun <T> time(label1: String, label2: String, operasjon: (() -> T)): T {
        val t0 = System.nanoTime()
        var status = "OK"
        try {
            val resultat = operasjon.invoke()
            return resultat
        } catch (e: Exception) {
            status = e.javaClass.simpleName
            throw e;
        } finally {
            tidsforbruk.labels(label1, label2, status)
                .observe(SimpleTimer.elapsedSecondsFromNanos(t0, System.nanoTime()))
        }
    }

    suspend fun <T> timeSuspended(label1: String, label2: String, operasjon: (suspend () -> T)): T {
        val t0 = System.nanoTime()
        var status = "OK"
        try {
            val resultat = operasjon.invoke()
            return resultat
        } catch (e: Exception) {
            status = e.javaClass.simpleName
            throw e;
        } finally {
            tidsforbruk.labels(label1, label2, status)
                .observe(SimpleTimer.elapsedSecondsFromNanos(t0, System.nanoTime()))
        }
    }
}