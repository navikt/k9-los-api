package no.nav.k9.los.utils

import java.time.LocalDateTime

class Cache <K, V>(val cacheSize : Int = 1000){
    private val map =
        object : LinkedHashMap<K, CacheObject<V>>(
        ) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<K, CacheObject<V>>): Boolean {
                return size > cacheSize
            }
        }

    fun set(key: K, value: CacheObject<V>) {
        map[key] = value
    }

    fun remove(key: K) = map.remove(key)

    fun get(key: K): CacheObject<V>? {
        return get(key, LocalDateTime.now())
    }

    fun get(key: K, now : LocalDateTime): CacheObject<V>? {
        val cacheObject = map[key] ?: return null
        if (cacheObject.expire.isBefore(now)) {
            remove(key)
            return null
        }
        return cacheObject
    }

    fun containsKey(key: K): Boolean {
        return containsKey(key, LocalDateTime.now())
    }

    fun containsKey(key: K, now : LocalDateTime): Boolean {
        val cacheObject = map[key] ?: return false
        if (cacheObject.expire.isBefore(now)) {
            remove(key)
            return false
        }
        return true
    }


    fun clear() {
        map.clear()
    }

    fun hent(nøkkel: K, populerCache: () -> V): V {
        val verdi = this.get(nøkkel)
        if (verdi == null) {
            val hentetVerdi = populerCache.invoke()
            this.set(nøkkel, CacheObject(value = hentetVerdi, expire = LocalDateTime.now().plusMinutes(30)))
            return hentetVerdi
        }
        return verdi.value
    }

}
