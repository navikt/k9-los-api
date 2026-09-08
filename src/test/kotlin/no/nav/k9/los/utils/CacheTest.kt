package no.nav.k9.los.utils

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import no.nav.k9.los.infrastruktur.utils.Cache
import no.nav.k9.los.infrastruktur.utils.CacheObject
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger

class CacheTest {

    @Test
    fun skal_sette_element_i_cache_og_hente_det_igjen() {
        val cache = Cache<String, String>(cacheSizeLimit = null)
        cache.set("foo", CacheObject("bar"))
        assertThat(cache.get("foo")?.value).isEqualTo("bar")
    }

    @Test
    fun skal_slette_først_element_dersom_antall_overstiger_maks() {
        val cache = Cache<String, String>(cacheSizeLimit = 1)
        cache.set("key1", CacheObject("bar1"))
        cache.set("key2", CacheObject("bar2"))
        assertThat(cache.get("key1")).isNull()
        assertThat(cache.get("key2")?.value).isEqualTo("bar2")
    }

    @Test
    fun skal_rydde_bort_elementer_som_har_gått_ut_på_tid() {
        val cache = Cache<String, String>(cacheSizeLimit = null)
        val t0 = LocalDateTime.now()
        cache.set("key1", CacheObject("bar1", expire = t0.plusMinutes(2)))
        cache.set("key2", CacheObject("bar2", expire = t0.plusMinutes(3)))
        cache.set("key3", CacheObject("bar3", expire = t0.plusMinutes(4)))

        cache.removeExpiredObjects(t0.plusMinutes(3).plusSeconds(30))

        assertThat(cache.get("key1")).isNull()
        assertThat(cache.get("key2")).isNull()
        assertThat(cache.get("key3")?.value).isEqualTo("bar3")
    }

    @Test
    fun `samtidige kall for samme nøkkel populerer kun en gang`() = runTest {
        val cache = Cache<String, String>()
        val startHenting = CompletableDeferred<Unit>()
        val fortsettHenting = CompletableDeferred<Unit>()
        val antallHentinger = AtomicInteger()

        val kall = (1..10).map {
            async {
                cache.hentSuspend("nøkkel") {
                    antallHentinger.incrementAndGet()
                    startHenting.complete(Unit)
                    fortsettHenting.await()
                    "verdi"
                }
            }
        }
        startHenting.await()
        fortsettHenting.complete(Unit)

        kall.awaitAll() shouldBe List(10) { "verdi" }
        antallHentinger.get() shouldBe 1
    }

    @Test
    fun `kall for ulike nøkler kan populeres parallelt`() = runTest {
        val cache = Cache<String, String>()
        val beggeStartet = CompletableDeferred<Unit>()
        val antallStartet = AtomicInteger()

        val kall = listOf("en", "to").map { nøkkel ->
            async {
                cache.hentSuspend(nøkkel) {
                    if (antallStartet.incrementAndGet() == 2) beggeStartet.complete(Unit)
                    beggeStartet.await()
                    nøkkel
                }
            }
        }

        kall.awaitAll() shouldBe listOf("en", "to")
    }

    @Test
    fun `suspensjon med trådbytte låser ikke cache`() = runTest {
        val cache = Cache<String, String>()

        cache.hentSuspend("nøkkel") {
            withContext(Dispatchers.Default) { "verdi" }
        } shouldBe "verdi"
        cache.hentSuspend("nøkkel") { error("skal ikke hente på nytt") } shouldBe "verdi"
    }

    @Test
    fun `feil rydder pågående henting slik at neste kall kan prøve igjen`() = runTest {
        val cache = Cache<String, String>()

        runCatching { cache.hentSuspend("nøkkel") { error("feil") } }

        cache.hentSuspend("nøkkel") { "verdi" } shouldBe "verdi"
    }

    @Test
    fun `cancellation rydder pågående henting slik at neste kall kan prøve igjen`() = runTest {
        val cache = Cache<String, String>()
        val førsteKall = backgroundScope.async {
            cache.hentSuspend("nøkkel") { awaitCancellation() }
        }
        runCurrent()

        førsteKall.cancelAndJoin()

        cache.hentSuspend("nøkkel") { "verdi" } shouldBe "verdi"
    }

    @Test
    fun `cancellation av den som henter lar ventende kall prøve igjen`() = runTest {
        val cache = Cache<String, String>()
        val førsteStartet = CompletableDeferred<Unit>()
        val førsteKall = backgroundScope.async {
            cache.hentSuspend("nøkkel") {
                førsteStartet.complete(Unit)
                awaitCancellation()
            }
        }
        førsteStartet.await()
        val ventendeKall = async {
            cache.hentSuspend("nøkkel") { "verdi" }
        }
        runCurrent()

        førsteKall.cancelAndJoin()

        ventendeKall.await() shouldBe "verdi"
    }
}
