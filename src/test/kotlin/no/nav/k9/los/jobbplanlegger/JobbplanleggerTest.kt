package no.nav.k9.los.jobbplanlegger

import assertk.assertThat
import assertk.assertions.isEqualTo
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
        val jobbplanlegger = Jobbplanlegger(backgroundScope, testTidtaker)
        var antallKjøringer = 0
        jobbplanlegger.planleggPeriodiskJobb(
            navn = "test-periodisk",
            prioritet = 1,
            intervall = 5.minutes,
            startForsinkelse = 1.minutes
        ) {
            antallKjøringer++
        }

        jobbplanlegger.start()

        advanceLocalTime(1.minutes)
        assertThat(antallKjøringer).isEqualTo(1)

        advanceLocalTime(5.minutes)
        assertThat(antallKjøringer).isEqualTo(2)

        jobbplanlegger.stopp()
    }

    @Test
    fun `test kjør på tidspunkt jobb`() = runTest {
        var jobKjørt = false
        val kjøreTidspunkt = testTid.plusMinutes(5)

        val jobbplanlegger = Jobbplanlegger(backgroundScope, testTidtaker)

        jobbplanlegger.planleggKjørPåTidspunktJobb(
            navn = "på-tidspunkt",
            prioritet = 1,
            tidspunkt = kjøreTidspunkt
        ) {
            jobKjørt = true
        }

        jobbplanlegger.start()
        advanceLocalTime(5.minutes)
        assertThat(jobKjørt).isTrue()

        jobbplanlegger.stopp()
    }

    @Test
    fun `test timejobb kjører på spesifiserte minutter`() = runTest {
        var antallKjøringer = 0
        val testMinutt = (testTid.minute + 2) % 60

        val jobbplanlegger = Jobbplanlegger(backgroundScope, testTidtaker)

        jobbplanlegger.planleggTimeJobb(
            navn = "time-jobb",
            prioritet = 1,
            minutter = listOf(testMinutt)
        ) {
            antallKjøringer++
        }

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
        val tidsvindu = Tidsvindu(
            listOf(
                DagligPeriode(
                    dager = setOf(testTid.dayOfWeek),
                    tidsperiode = Tidsperiode(13, 14)
                )
            )
        )

        val jobbplanlegger = Jobbplanlegger(backgroundScope, testTidtaker)

        jobbplanlegger.planleggPeriodiskJobb(
            navn = "tidsvindu-jobb",
            prioritet = 1,
            intervall = 30.minutes,
            startForsinkelse = 1.minutes,
            tidsvindu = tidsvindu
        ) {
            antallKjøringer++
        }

        jobbplanlegger.start()
        advanceLocalTime(61.minutes)
        assertThat(antallKjøringer).isEqualTo(1)

        advanceLocalTime(30.minutes)
        assertThat(antallKjøringer).isEqualTo(2)

        advanceLocalTime(30.minutes)
        assertThat(antallKjøringer).isEqualTo(2)

        jobbplanlegger.stopp()
    }

    @Test
    fun `test prioritering av jobber`() = runTest {
        val rekkefølge = mutableListOf<Int>()
        val jobbplanlegger = Jobbplanlegger(backgroundScope, testTidtaker)

        jobbplanlegger.planleggOppstartJobb(
            navn = "lav-prioritet",
            prioritet = 2
        ) {
            rekkefølge.add(2)
        }

        jobbplanlegger.planleggOppstartJobb(
            navn = "høy-prioritet",
            prioritet = 1
        ) {
            rekkefølge.add(1)
            delay(2.minutes)
        }

        jobbplanlegger.start()
        advanceLocalTime(1.minutes)
        assertThat(rekkefølge).isEqualTo(listOf(1))

        advanceLocalTime(1.minutes + 1.seconds)
        assertThat(rekkefølge).isEqualTo(listOf(1, 2))

        jobbplanlegger.stopp()
    }
}