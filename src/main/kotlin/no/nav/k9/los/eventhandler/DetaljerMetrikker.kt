package no.nav.k9.los.eventhandler

import io.prometheus.client.Counter
import io.prometheus.client.Histogram
import io.prometheus.client.SimpleTimer
import kotlin.math.pow

object DetaljerMetrikker {

    private val tidsforbruk = Histogram.build()
        .name("k9los_internal")
        .help("Tidsforbruk diverse internt")
        .labelNames("label1", "label2", "resultat")
        .exponentialBuckets(0.01, 10.0.pow(1.0 / 3.0), 12)
        .register()

    private val teller = Histogram.build()
        .name("k9los_internal_teller")
        .help("Teller diverse internt")
        .labelNames("label1", "label2")
        .exponentialBuckets(1.0, 2.0, 10)
        .register()

    fun <T> time(label1: String, label2: String, operasjon: (() -> T)): T {
        val t0 = System.nanoTime()
        var status = "OK"
        try {
            val resultat = operasjon.invoke()
            return resultat
        } catch (e: Exception) {
            status = e.javaClass.simpleName
            throw e
        } finally {
            observe(t0, label1, label2, status)
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
            throw e
        } finally {
            observe(t0, label1, label2, status)
        }
    }

    fun observe(starttidNanos : Long, label1: String, label2: String, status : String= "OK") {
        tidsforbruk.labels(label1, label2, status)
            .observe(SimpleTimer.elapsedSecondsFromNanos(starttidNanos, System.nanoTime()))
    }

    fun observeTeller(label1: String, label2: String, antall : Number) {
        teller.labels(label1, label2)
            .observe(antall.toDouble())
    }
}