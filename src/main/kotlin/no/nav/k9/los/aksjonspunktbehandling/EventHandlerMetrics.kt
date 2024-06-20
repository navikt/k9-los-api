package no.nav.k9.los.aksjonspunktbehandling

import io.prometheus.client.Histogram
import io.prometheus.client.SimpleTimer

class EventHandlerMetrics {

    companion object {
        private val tidsforbruk = Histogram.build()
            .name("k9los_event_handler")
            .labelNames("avsenderSystem", "hendelse", "status")
            .help("Tidsforbruk håndtering av kafka hendelse til k9los")
            .exponentialBuckets(0.001, Math.pow(10.0, 1.0/3.0), 12 )
            .register()

        fun observe(avsenderSystem : String, hendelse : String, starttidNanos : Long, status : String = "OK"){
            tidsforbruk.labels(avsenderSystem, hendelse, status).observe(SimpleTimer.elapsedSecondsFromNanos(starttidNanos, System.nanoTime()))
        }

        fun <T> time(avsenderSystem: String, hendelse: String, starttid : Long = System.nanoTime(), operasjon : (() -> T)) : T {
            var status = "OK"
            try {
                val resultat = operasjon.invoke()
                return resultat
            } catch (e : Exception){
                status = e.javaClass.simpleName
                throw e;
            } finally {
                tidsforbruk.labels(avsenderSystem, hendelse, status).observe(SimpleTimer.elapsedSecondsFromNanos(starttid, System.nanoTime()))
            }
        }

        suspend fun <T> timeSuspended(avsenderSystem: String, hendelse: String, starttid : Long = System.nanoTime(), operasjon : ( suspend () -> T)) : T {
            var status = "OK"
            try {
                val resultat = operasjon.invoke()
                return resultat
            } catch (e : Exception){
                status = e.javaClass.simpleName
                throw e;
            } finally {
                tidsforbruk.labels(avsenderSystem, hendelse, status).observe(SimpleTimer.elapsedSecondsFromNanos(starttid, System.nanoTime()))
            }
        }
    }
}