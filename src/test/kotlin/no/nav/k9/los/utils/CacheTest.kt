package no.nav.k9.los.utils

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

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
}