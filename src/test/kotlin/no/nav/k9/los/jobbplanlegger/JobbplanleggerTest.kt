package no.nav.k9.los.jobbplanlegger

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class JobbplanleggerTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var testScope: TestScope
    private lateinit var jobbplanlegger: Jobbplanlegger

    @BeforeEach
    fun setup() {
        testScope = TestScope(testDispatcher)
        jobbplanlegger = Jobbplanlegger(testScope.backgroundScope)
    }

    @AfterEach
    fun tearDown() {
        jobbplanlegger.stopp()
    }

    @Test
    fun `test oppstartjobb`() = testScope.runTest {
        var jobKjørt = false
        jobbplanlegger.planleggOppstartJobb(
            navn = "test-jobb",
            prioritet = 1
        ) {
            jobKjørt = true
        }

        jobbplanlegger.start()
        advanceTimeBy(1.seconds)
        jobbplanlegger.stopp()

        assertTrue(jobKjørt)
    }

    @Test
    fun `test periodisk jobb kjører med riktig intervall`() = testScope.runTest {
        var antallKjøringer = 0
        val startForsinkelse = 1.seconds
        val intervall = 5.seconds

        jobbplanlegger.planleggPeriodiskJobb(
            navn = "test-periodisk",
            prioritet = 1,
            intervall = intervall,
            startForsinkelse = startForsinkelse
        ) {
            antallKjøringer++
        }

        jobbplanlegger.start()
        advanceTimeBy(startForsinkelse)  // Første kjøring
        assertEquals(1, antallKjøringer)

        advanceTimeBy(intervall)  // Andre kjøring
        assertEquals(2, antallKjøringer)

        jobbplanlegger.stopp()
    }

    @Test
    fun `test kjør på tidspunkt jobb`() = testScope.runTest {
        var jobKjørt = false
        val kjøreTidspunkt = LocalDateTime.now().plusSeconds(5)

        jobbplanlegger.planleggKjørPåTidspunktJobb(
            navn = "på-tidspunkt",
            prioritet = 1,
            tidspunkt = kjøreTidspunkt
        ) {
            jobKjørt = true
        }

        jobbplanlegger.start()
        advanceTimeBy(6.seconds)
        jobbplanlegger.stopp()

        assertTrue(jobKjørt)
    }
}