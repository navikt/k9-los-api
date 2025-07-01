package no.nav.k9.los.nyoppgavestyring.infrastruktur.utils

import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.locks.ReentrantLock
import java.util.concurrent.locks.ReentrantReadWriteLock

open class Cache<K, V>(val cacheSizeLimit: Int?) {

    private val readWriteLock = ReentrantReadWriteLock(true)

    protected val keyValueMap = object : LinkedHashMap<K, CacheObject<V>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, CacheObject<V>>): Boolean {
            return cacheSizeLimit != null && size > cacheSizeLimit
        }
    }
    private val låserForHentFunksjon: MutableMap<K, ReentrantLock> = HashMap()

    fun set(key: K, value: CacheObject<V>) {
        withWriteLock {
            keyValueMap[key] = value
        }
    }

    fun remove(key: K) {
        withWriteLock { keyValueMap.remove(key) }
    }

    fun get(key: K): CacheObject<V>? {
        return get(key, LocalDateTime.now())
    }

    fun get(key: K, now: LocalDateTime): CacheObject<V>? {
        val cacheObject = withReadLock { keyValueMap[key] } ?: return null
        if (cacheObject.expire.isBefore(now)) {
            withWriteLock { remove(key) }
            return null
        }
        return cacheObject
    }

    fun containsKey(key: K, now: LocalDateTime): Boolean {
        val cacheObject = withReadLock { keyValueMap[key] } ?: return false
        if (cacheObject.expire.isBefore(now)) {
            withWriteLock { remove(key) }
            return false
        }
        return true
    }

    fun clear() {
        withWriteLock { keyValueMap.clear() }
    }

    @WithSpan
    fun removeExpiredObjects(now: LocalDateTime) {
        withWriteLock {
            val expiredKeys = keyValueMap.entries.filter { it.value.expire < now }.map { it.key }
            expiredKeys.forEach { keyValueMap.remove(it) }
        }
    }

    fun hent(nøkkel: K, populerCache: () -> V): V {
        return hent(nøkkel, Duration.ofMinutes(30), populerCache)
    }

    suspend fun hentSuspend(nøkkel: K, populerCache: suspend () -> V): V {
        return hentSuspend(nøkkel, Duration.ofMinutes(30), populerCache)
    }

    @WithSpan
    fun hent(nøkkel: K, duration: Duration, populerCache: () -> V): V {
        get(nøkkel)?.let { return it.value }

        //egen lås pr nøkkel for å kunne oppdatere for flere nøkler samtidig, og samtidig unngå at flere tråder forsøker å kjøre unødvendige kall for samme nøkkel
        val låsForHenting = finnLåsForHenting(nøkkel)
        låsForHenting.lock()
        try {
            //sjekk på nytt for å unngå å hente om en annen tråd allerde har gjort det
            get(nøkkel)?.let { return it.value }

            val hentetVerdi = OpentelemetrySpanUtil.span("cache-hent-verdi") { populerCache.invoke() }
            this.set(nøkkel, CacheObject(value = hentetVerdi, expire = LocalDateTime.now().plus(duration)))
            return hentetVerdi
        } finally {
            låsForHenting.unlock()
            withWriteLock { låserForHentFunksjon.remove(nøkkel) }
        }
    }

    @WithSpan
    suspend fun hentSuspend(nøkkel: K, duration: Duration, populerCache: suspend () -> V): V {
        get(nøkkel)?.let { return it.value }

        //egen lås pr nøkkel for å kunne oppdatere for flere nøkler samtidig, og samtidig unngå at flere tråder forsøker å kjøre unødvendige kall for samme nøkkel
        val låsForHenting = finnLåsForHenting(nøkkel)
        låsForHenting.lock()
        try {
            //sjekk på nytt for å unngå å hente om en annen tråd allerde har gjort det
            get(nøkkel)?.let { return it.value }

            val hentetVerdi = OpentelemetrySpanUtil.spanSuspend("cache-hent-verdi") { populerCache.invoke() }
            this.set(nøkkel, CacheObject(value = hentetVerdi, expire = LocalDateTime.now().plus(duration)))
            return hentetVerdi
        } finally {
            låsForHenting.unlock()
            withWriteLock { låserForHentFunksjon.remove(nøkkel) }
        }
    }

    private fun finnLåsForHenting(nøkkel: K) = withWriteLock {
        var lås = låserForHentFunksjon.get(nøkkel)
        if (lås == null) {
            lås = ReentrantLock()
            låserForHentFunksjon.put(nøkkel, lås)
        }
        lås
    }

    protected fun <V> withReadLock(operasjon: () -> V): V {
        if (readWriteLock.isWriteLockedByCurrentThread) {
            throw IllegalStateException("Deadlock beskyttelse, får ikke lov å ta read lock når write lock er tatt (siden motsatt rekkefølge tillates)")
        }
        readWriteLock.readLock().lock()
        try {
            return operasjon.invoke();
        } finally {
            readWriteLock.readLock().unlock()
        }
    }

    protected fun <V> withWriteLock(operasjon: () -> V): V {

        readWriteLock.writeLock().lock()
        try {
            return operasjon.invoke();
        } finally {
            readWriteLock.writeLock().unlock()
        }
    }
}
