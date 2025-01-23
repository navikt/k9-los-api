package no.nav.k9.los.prioritertjobb

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertTrue
import kotlin.test.fail
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


@OptIn(ExperimentalCoroutinesApi::class)
class PrioritertJobbPlanleggerTest {
    private lateinit var testDispatcher: TestDispatcher

    @BeforeEach
    fun setup() {
        testDispatcher = StandardTestDispatcher()
    }

    /*@Test
    fun `test at intervall-jobb kjører flere ganger`() = runTest(testDispatcher) {
        val kjøringer = AtomicInteger(0)

        val planlegger = PrioritertJobbPlanlegger(
            //coroutineScope = this,
            jobber = mutableListOf(
                PrioritertJobb(
                    navn = "IntervallJobb",
                    prioritet = 1,
                    plan = JobbPlan.FastIntervall(100.milliseconds)
                ) {
                    kjøringer.incrementAndGet()
                }
            )
        )

        planlegger.startJobber()
        delay(250) // Vent litt lengre enn 2 intervaller

        planlegger.stoppAlleJobber()

        assertTrue(
            kjøringer.get() >= 2,
            "Forventet minst 2 kjøringer, fikk ${kjøringer.get()}"
        )
    }

    @Test
    fun `test at høyere prioritet kjører før lavere prioritet`() = runTest(testDispatcher) {
        val kjøringsRekkefølge = mutableListOf<String>()

        val planlegger = PrioritertJobbPlanlegger(
            jobber = mutableListOf(
                PrioritertJobb(
                    navn = "LavPrioritet",
                    prioritet = 2,
                    plan = JobbPlan.FastIntervall(100.milliseconds)
                ) {
                    kjøringsRekkefølge.add("Lav")
                },
                PrioritertJobb(
                    navn = "HøyPrioritet",
                    prioritet = 1,
                    plan = JobbPlan.FastIntervall(100.milliseconds)
                ) {
                    kjøringsRekkefølge.add("Høy")
                }
            )
        )

        planlegger.startJobber()
        delay(150) // Vent litt lengre enn ett intervall

        planlegger.stoppAlleJobber()

        // Sjekk at høy prioritet kommer først i hver runde
        var forrigeHøy = -1
        var forrigeLav: Int

        kjøringsRekkefølge.forEachIndexed { index, verdi ->
            if (verdi == "Høy") {
                forrigeHøy = index
            } else {
                forrigeLav = index
                if (forrigeHøy == -1 || forrigeHøy > forrigeLav) {
                    fail("Lav prioritet kjørte før høy prioritet")
                }
            }
        }
    }

    @Test
    fun `test at intervall-jobb kan stoppes`() = runTest(testDispatcher) {
        val kjøringer = AtomicInteger(0)

        val planlegger = PrioritertJobbPlanlegger(
            jobber = mutableListOf(
                PrioritertJobb(
                    navn = "StoppTest",
                    prioritet = 1,
                    plan = JobbPlan.FastIntervall(100.milliseconds)
                ) {
                    kjøringer.incrementAndGet()
                }
            )
        )

        planlegger.startJobber()
        delay(150) // La jobben kjøre litt
        planlegger.stoppJobb("StoppTest")

        val antallKjøringer = kjøringer.get()
        delay(200) // Vent litt til for å se om jobben fortsetter

        assertEquals(
            antallKjøringer, kjøringer.get(),
            "Jobben fortsatte å kjøre etter at den ble stoppet"
        )
    }

    /*@Test
    fun `test prioritering av ulike typer jobber`() = runTest(testDispatcher) {
        val kjøringsLogg = Collections.synchronizedList(mutableListOf<String>())

        val planlegger = PrioritertJobbPlanlegger(
            coroutineScope = this,
            jobber = mutableListOf(
                PrioritertJobb(
                    navn = "HøyPrioritetIntervall",
                    prioritet = 1,
                    plan = JobbPlan.FastIntervall(100.milliseconds)
                ) {
                    kjøringsLogg.add("HøyIntervall-Start")
                    delay(200)  // Lang kjøretid
                    kjøringsLogg.add("HøyIntervall-Slutt")
                },
                PrioritertJobb(
                    navn = "HøyPrioritetForsinket",
                    prioritet = 1,
                    plan = JobbPlan.ForsnketStart0.milliseconds)
                ) {
                    kjøringsLogg.add("HøyForsinket-Start")
                    delay(150)  // Middels kjøretid
                    kjøringsLogg.add("HøyForsinket-Slutt")
                },
                PrioritertJobb(
                    navn = "LavPrioritetIntervall",
                    prioritet = 2,
                    plan = JobbPlan.FastIntervall(150.milliseconds)
                ) {
                    kjøringsLogg.add("LavIntervall-Start")
                    delay(100)  // Kort kjøretid
                    kjøringsLogg.add("LavIntervall-Slutt")
                }
            )
        )

        try {
            planlegger.startJobber()

            // La jobbene kjøre en stund, men bruk advanceTimeBy for bedre kontroll
            repeat(4) {  // Gjør 4 tidshopp på 100ms hver
                delay(100)
                advanceTimeBy(100)
            }

        } finally {
            planlegger.stoppAlle()  // Sikre at vi alltid stopper jobbene
        }

        // Vent til alle jobber har fullført
        advanceUntilIdle()

        println("Kjøringslogg: ${kjøringsLogg.joinToString(", ")}")

        // Verifiser at høyprioriterte jobber starter først
        val førsteLavPrioritetStart = kjøringsLogg.indexOfFirst { it == "LavIntervall-Start" }
        val førsteHøyIntervallStart = kjøringsLogg.indexOfFirst { it == "HøyIntervall-Start" }
        val høyForsinketStart = kjøringsLogg.indexOfFirst { it == "HøyForsinket-Start" }

        assertTrue(førsteHøyIntervallStart >= 0, "HøyIntervall startet ikke")
        assertTrue(høyForsinketStart >= 0, "HøyForsinket startet ikke")
        assertTrue(førsteLavPrioritetStart >= 0, "LavIntervall startet ikke")

        // Lav prioritet skal ikke starte før minst én høy prioritet har startet
        assertTrue(
            førsteHøyIntervallStart < førsteLavPrioritetStart ||
                    høyForsinketStart < førsteLavPrioritetStart,
            "Minst én høyprioritert jobb skulle startet før lavprioritert"
        )

        // Sjekk at vi har flere kjøringer av høyprioritets intervalljobben
        val antallHøyIntervall = kjøringsLogg.count { it == "HøyIntervall-Start" }
        assertTrue(antallHøyIntervall > 1, "HøyIntervall skulle kjørt flere ganger")

        // Sjekk at forsinket start bare kjører én gang
        val antallHøyForsinket = kjøringsLogg.count { it == "HøyForsinket-Start" }
        assertEquals(1, antallHøyForsinket, "HøyForsinket skulle bare kjørt én gang")

        // Sjekk at lavprioritets intervalljobben får kjørt
        val antallLavIntervall = kjøringsLogg.count { it == "LavIntervall-Start" }
        assertTrue(antallLavIntervall > 0, "LavIntervall skulle kjørt minst én gang")
    }*/

    @Test
    fun `test at jobb respekterer tidsvindu`() = runTest(testDispatcher) {
        val kjøringsLogg = Collections.synchronizedList(mutableListOf<String>())
        val testTid = AtomicReference(
            LocalDateTime.of(2025, 1, 1, 7, 0) // Onsdag kl 07:00
        )

        val planlegger = PrioritertJobbPlanlegger(
            nå = { testTid.get()},
            jobber = mutableListOf(
                PrioritertJobb(
                    navn = "ArbeidstidJobb",
                    prioritet = 1,
                    plan = JobbPlan.FastIntervall(
                        intervall = 100.milliseconds,
                        tidsvindu = Tidsvindu.ARBEIDSTID
                    )
                ) {
                    kjøringsLogg.add("Arbeidstid-${testTid.get().toLocalTime()}")
                }
            )
        )

        try {
            planlegger.startJobber()

            // Test før arbeidstid (kl 07:00) - skal ikke kjøre
            delay(200)
            advanceTimeBy(200)

            // Test i arbeidstid (kl 09:00) - skal kjøre
            testTid.set(LocalDateTime.of(2025, 1, 1, 9, 0))
            delay(200)
            advanceTimeBy(200)

            // Test etter arbeidstid (kl 17:30) - skal ikke kjøre
            testTid.set(LocalDateTime.of(2025, 1, 1, 17, 30))
            delay(200)
            advanceTimeBy(200)

            // Test i helgen - skal ikke kjøre
            testTid.set(LocalDateTime.of(2025, 1, 4, 9, 0)) // Lørdag
            delay(200)
            advanceTimeBy(200)
        } finally {
            planlegger.stoppAlleJobber()
        }

        println("Kjøringslogg: ${kjøringsLogg.joinToString(", ")}")

        // Verifiser at jobben bare kjørte i arbeidstiden
        assertTrue(kjøringsLogg.isNotEmpty(), "Jobben skulle kjørt minst én gang i arbeidstiden")
        assertTrue(
            kjøringsLogg.all { it.contains("09:") },
            "Jobben kjørte utenfor arbeidstid: $kjøringsLogg"
        )
    }

    @Test
    fun `test nattjobb`() = runTest(testDispatcher) {
        val kjøringsLogg = Collections.synchronizedList(mutableListOf<String>())
        val testTid =
            AtomicReference(LocalDateTime.of(2025, 1, 1, 23, 0, 0, 0)) // Natt til lørdag kl 23:00

        val planlegger = PrioritertJobbPlanlegger(
            scope = this,
            nå = { testTid.get() },
            jobber = mutableListOf(
                PrioritertJobb(
                    navn = "Nattjobb",
                    prioritet = 1,
                    plan = JobbPlan.FastIntervall(
                        intervall = 100.seconds,
                        tidsvindu = Tidsvindu.NATT
                    )
                ) {
                    kjøringsLogg.add("Natt-${testTid.get().format(DateTimeFormatter.ISO_LOCAL_TIME)}")
                    delay(100)
                    testTid.set(testTid.get().plus(100, ChronoUnit.MILLIS))
                    advanceTimeBy(100)
                }
            )
        )

        try {
            planlegger.startJobber()

            // Test på natten (kl 23:00) - skal kjøre
            delay(100)
            testTid.set(testTid.get().plus(100, ChronoUnit.MILLIS))
            advanceTimeBy(100)

            delay(2.hours)
            testTid.set(testTid.get().plus(2, ChronoUnit.HOURS))
            advanceTimeBy(14.hours)
        } finally {
            planlegger.stoppAlleJobber()
        }

        println("Kjøringslogg: ${kjøringsLogg.joinToString(", ")}")

        // Verifiser at jobben bare kjørte på natten
        assertTrue(kjøringsLogg.isNotEmpty(), "Jobben skulle kjørt minst én gang på natten")
        assertTrue(
            kjøringsLogg.all { it.contains("23:") },
            "Jobben kjørte utenfor nattid: $kjøringsLogg"
        )
    }*/
}