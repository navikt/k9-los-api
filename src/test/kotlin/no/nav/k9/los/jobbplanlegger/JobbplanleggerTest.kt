package no.nav.k9.los.jobbplanlegger

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

@OptIn(ExperimentalCoroutinesApi::class)
class JobbplanleggerTest {
    private lateinit var testTid: LocalDateTime
    private val testTidtaker = { testTid }

    @BeforeEach
    fun setup() {
        testTid = LocalDateTime.of(2024, 1, 1, 12, 0)
    }

    private fun TestScope.advanceLocalTime(duration: Duration) {
        testTid = testTid.plus(duration.toJavaDuration())
        advanceTimeBy(duration)
    }

    @Test
    fun `test periodisk jobb kjører med riktig intervall`() = runTest {
        var antallKjøringer = 0

        val jobbplanlegger = Jobbplanlegger(
            setOf(
                PlanlagtJobb.Periodisk(
                    navn = "test-periodisk",
                    prioritet = 1,
                    intervall = 5.minutes,
                    startForsinkelse = 1.minutes,
                    blokk = {
                        antallKjøringer++
                    }
                )
            ), backgroundScope, testTidtaker
        )
        jobbplanlegger.start()

        advanceLocalTime(1.minutes)
        assertThat(antallKjøringer).isEqualTo(1)

        advanceLocalTime(5.minutes)
        assertThat(antallKjøringer).isEqualTo(2)

        jobbplanlegger.stopp()
    }

    @Test
    fun `test kjør før tidligst-tidspunkt jobb`() = runTest {
        var jobKjørt = false
        val kjøreTidspunkt = testTid.plusMinutes(5)

        val jobbplanlegger = Jobbplanlegger(
            setOf(
                PlanlagtJobb.KjørPåTidspunkt(
                    navn = "tidligst-tidspunkt",
                    prioritet = 1,
                    kjørTidligst = kjøreTidspunkt,
                    blokk = { jobKjørt = true }
                )
            ), backgroundScope, testTidtaker
        )

        jobbplanlegger.start()
        advanceLocalTime(5.minutes)
        assertThat(jobKjørt).isTrue()

        jobbplanlegger.stopp()
    }

    @Test
    fun `test kjør ikke senere enn tidspunkt jobb`() = runTest {
        var jobKjørt = false

        val jobbplanlegger = Jobbplanlegger(
            setOf(
                PlanlagtJobb.KjørPåTidspunkt(
                    navn = "senest-tidspunkt",
                    prioritet = 1,
                    kjørSenest = testTid.plusMinutes(5),
                    blokk = { jobKjørt = true }
                )
            ), backgroundScope, testTidtaker
        )
        advanceLocalTime(5.minutes + 1.seconds)
        jobbplanlegger.start()

        for (i in 1..4) {
            advanceLocalTime(1.seconds)
            assertThat(jobKjørt).isFalse()
        }

        jobbplanlegger.stopp()
    }

    @Test
    fun `test timejobb kjører på spesifiserte minutter`() = runTest {
        var antallKjøringer = 0
        val testMinutt = (testTid.minute + 2) % 60

        val jobbplanlegger = Jobbplanlegger(
            setOf(
                PlanlagtJobb.TimeJobb(
                    navn = "time-jobb",
                    prioritet = 1,
                    tidsvindu = Tidsvindu.ÅPENT,
                    minutter = listOf(testMinutt),
                    blokk = { antallKjøringer++ }
                )
            ), backgroundScope, testTidtaker
        )

        jobbplanlegger.start()
        advanceLocalTime(2.minutes)
        assertThat(antallKjøringer).isEqualTo(1)

        advanceLocalTime(60.minutes)
        assertThat(antallKjøringer).isEqualTo(2)

        jobbplanlegger.stopp()
    }

    @Test
    fun `test jobb respekterer tidsvindu`() = runTest {
        var antallKjøringer = 0

        val jobbplanlegger = Jobbplanlegger(
            setOf(
                PlanlagtJobb.Periodisk(
                    navn = "tidsvindu-jobb",
                    prioritet = 1,
                    intervall = 30.minutes,
                    startForsinkelse = 1.minutes,
                    tidsvindu = TidsvinduMedPerioder(
                        listOf(
                            DagligPeriode(
                                dag = testTid.dayOfWeek,
                                tidsperiode = Tidsperiode(13, 14)
                            )
                        )
                    ),
                    blokk = { antallKjøringer++ }
                )
            ), backgroundScope, testTidtaker
        )

        jobbplanlegger.start()
        advanceLocalTime(61.minutes)
        assertThat(antallKjøringer).isEqualTo(1)

        advanceLocalTime(29.minutes)
        assertThat(antallKjøringer).isEqualTo(1)
        advanceLocalTime(1.minutes)
        assertThat(antallKjøringer).isEqualTo(2)

        jobbplanlegger.stopp()
    }

    @Test
    fun `test uker frem i tid`() = runTest {
        var antallKjøringer = 0

        testTid = LocalDateTime.of(2024, 12, 31, 12, 0)

        val jobbplanlegger = Jobbplanlegger(
            setOf(
                PlanlagtJobb.Periodisk(
                    navn = "tidsvindu-jobb",
                    prioritet = 1,
                    intervall = 1.days,
                    startForsinkelse = 1.minutes,
                    tidsvindu = Tidsvindu.hverdager(10, 11),
                    blokk = { antallKjøringer++ }
                )
            ), backgroundScope, testTidtaker, ventetidMellomJobber = 1.hours
        )

        jobbplanlegger.start()
        repeat(50) {
            advanceLocalTime(1.days)
        }
        assertThat(antallKjøringer, "Skal være 36 hverdager 50 dager fra 31/12/24, så 36 kjøringer").isEqualTo(36)

        jobbplanlegger.stopp()
    }

    @Test
    fun `test prioritering av jobber`() = runTest {
        val rekkefølge = mutableListOf<String>()
        val jobbplanlegger = Jobbplanlegger(
            setOf(
                PlanlagtJobb.KjørPåTidspunkt(
                    navn = "lav-prioritet",
                    prioritet = 2,
                    kjørTidligst = testTid.plusMinutes(1),
                    blokk = {
                        rekkefølge.add("lav")
                    }
                ),
                PlanlagtJobb.KjørPåTidspunkt(
                    navn = "høy-prioritet",
                    prioritet = 1,
                    kjørTidligst = testTid,
                    blokk = {
                        rekkefølge.add("høy")
                        delay(2.minutes)
                    }
                )
            ), backgroundScope, testTidtaker
        )
        jobbplanlegger.start()
        advanceLocalTime(1.minutes + 1.milliseconds)
        assertThat(rekkefølge).isEqualTo(listOf("høy"))

        advanceLocalTime(1.minutes)
        assertThat(rekkefølge).isEqualTo(listOf("høy", "lav"))

        jobbplanlegger.stopp()
    }
}