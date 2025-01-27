package no.nav.k9.los.jobbplanlegger

import kotlinx.coroutines.CoroutineScope
import no.nav.k9.los.jobbplanlegger.PlanlagtJobb.KjørPåTidspunkt
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class JobbplanleggerBuilder(
    private val scope: CoroutineScope,
    private val tidtaker: () -> LocalDateTime = { LocalDateTime.now() },
    private val ventetidMellomJobber: Duration = 1.seconds,
) {
    private val jobber = mutableMapOf<String, PlanlagtJobb>()
    private val log = LoggerFactory.getLogger(JobbplanleggerBuilder::class.java)

    fun planleggOppstartJobb(
        navn: String,
        prioritet: Int,
        blokk: suspend CoroutineScope.() -> Unit
    ) {
        leggTilJobb(PlanlagtJobb.Oppstart(navn = navn, prioritet = prioritet, blokk = blokk))
    }

    fun planleggPeriodiskJobb(
        navn: String,
        prioritet: Int,
        intervall: Duration,
        startForsinkelse: Duration = intervall,
        tidsvindu: Tidsvindu = Tidsvindu.ÅPENT,
        blokk: suspend CoroutineScope.() -> Unit
    ) {
        if (intervall < ventetidMellomJobber) {
            log.warn("Intervall for jobb '$navn' ($intervall) er kortere enn ventetiden mellom jobber ($ventetidMellomJobber)")
        }
        val jobb = PlanlagtJobb.Periodisk(
            navn = navn,
            prioritet = prioritet,
            tidsvindu = tidsvindu,
            intervall = intervall,
            startForsinkelse = startForsinkelse,
            blokk = blokk
        )
        leggTilJobb(jobb)
    }

    fun planleggTimeJobb(
        navn: String,
        prioritet: Int,
        minutter: List<Int>,
        tidsvindu: Tidsvindu = Tidsvindu.ÅPENT,
        blokk: suspend CoroutineScope.() -> Unit
    ) {
        require(minutter.all { it in 0..59 }) { "Minutter må være mellom 0 og 59" }
        leggTilJobb(
            PlanlagtJobb.TimeJobb(
                navn = navn,
                prioritet = prioritet,
                tidsvindu = tidsvindu,
                minutter = minutter.sorted(),
                blokk = blokk
            )
        )
    }

    fun planleggKjørPåTidspunktJobb(
        navn: String,
        prioritet: Int,
        kjørTidligst: LocalDateTime = LocalDateTime.MIN,
        kjørSenest: LocalDateTime = LocalDateTime.MAX,
        blokk: suspend CoroutineScope.() -> Unit
    ) {
        require(kjørSenest > kjørTidligst) { "kjørSenest må være etter kjørTidligst" }
        val jobb = KjørPåTidspunkt(navn, prioritet, kjørTidligst, kjørSenest, blokk)
        leggTilJobb(jobb)
    }

    private fun leggTilJobb(
        jobb: PlanlagtJobb,
    ) {
        if (jobber.put(jobb.navn, jobb) != null) {
            throw IllegalArgumentException("Jobb med navn ${jobb.navn} er allerede lagt til")
        }
    }

    fun build(): Jobbplanlegger {
        return Jobbplanlegger(
            scope = scope,
            planlagteJobber = jobber.values.toSet(),
            tidtaker = tidtaker,
            ventetidMellomJobber = ventetidMellomJobber
        )
    }
}