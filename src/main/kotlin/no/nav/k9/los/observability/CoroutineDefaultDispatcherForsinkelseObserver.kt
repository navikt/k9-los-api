package no.nav.k9.los.observability

import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.*
import kotlin.collections.ArrayList
import kotlin.concurrent.timer

/**
 * Dersom det skjer for mye IO/blokkerende operasjonen på default dispatcher-trådene, vil det oppstå forsinkelser i
 * systemet uten at det er lett å se hvorfor.
 *
 * Denne klassen måler forsinkelse hyppig, slik at det skal være enklere å oppdage om det er problem på vei
 */
class CoroutineDefaultDispatcherForsinkelseObserver(
    val tidMellomKjøring: Duration = Duration.ofSeconds(1),
    val forsinketOppstart: Duration = Duration.ofSeconds(10),
    val grenseWarning: Duration = Duration.ofMillis(500),
    var målingerPrRapport : Int = 30, //for å kunne måle hyppigere enn det logges, for å unngå å spamme loggen
    var sisteMålinger : MutableList<Long> = ArrayList()
) {
    private val TRÅDNAVN = "k9los-coroutine-dispatcher-forsinkelse-observer"
    private val log = LoggerFactory.getLogger(CoroutineDefaultDispatcherForsinkelseObserver::class.java)

    fun startSjekkAvForsinkelse(): Timer {
        return timer(
            daemon = true,
            name = TRÅDNAVN,
            period = tidMellomKjøring.toMillis(),
            initialDelay = forsinketOppstart.toMillis()
        ) {
            val t0 = System.currentTimeMillis()
            runBlocking {
                val t1 = System.currentTimeMillis()
                val diff = t1 - t0

                sisteMålinger.add(diff)
                if (sisteMålinger.size == målingerPrRapport){
                    val min = sisteMålinger.stream().min(Comparator.naturalOrder())
                    val max = sisteMålinger.stream().max(Comparator.naturalOrder())
                    val sum = sisteMålinger.stream().reduce { a, b -> a + b }.orElse(0)
                    val avg = sum / målingerPrRapport;
                    sisteMålinger.clear()
                    val melding = "Forsinkelse i default-dispatcher: snitt ${avg}ms, maks ${max}ms, min ${min}ms"
                    if (avg > grenseWarning.toMillis()){
                        log.warn(melding)
                    } else {
                        log.info(melding)
                    }
                }
            }
        }
    }
}