package no.nav.k9.los.utils

import io.opentelemetry.instrumentation.annotations.WithSpan
import java.time.LocalDateTime
import java.util.concurrent.locks.ReentrantReadWriteLock

class Cache<K, V>(val cacheSizeLimit: Int?) {

    private val readWriteLock = ReentrantReadWriteLock()

    private val keyValueMap = object : LinkedHashMap<K, CacheObject<V>>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, CacheObject<V>>): Boolean {
            return cacheSizeLimit != null && size > cacheSizeLimit
        }
    }

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
        return withWriteLock {
            val verdi = this.get(nøkkel)
            if (verdi == null) {
                val hentetVerdi = populerCache.invoke()
                this.set(nøkkel, CacheObject(value = hentetVerdi, expire = LocalDateTime.now().plusMinutes(30)))
                hentetVerdi
            } else {
                verdi.value
            }
        }
    }

    private fun <V> withReadLock(operasjon: () -> V): V {
        readWriteLock.readLock().lock()
        try {
            return operasjon.invoke();
        } finally {
            readWriteLock.readLock().unlock()
        }
    }

    private fun <V> withWriteLock(operasjon: () -> V): V {
        readWriteLock.writeLock().lock()
        try {
            return operasjon.invoke();
        } finally {
            readWriteLock.writeLock().unlock()
        }
    }
}
