package no.nav.k9.utils

import java.time.LocalDateTime

class Cache <T>(val cacheSize : Int = 1000){
    private val map =
        object : LinkedHashMap<String, CacheObject<T>>(
        ) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheObject<T>>): Boolean {
                return size > cacheSize
            }
        }

    fun set(key: String, value: CacheObject<T>) {
        map[key] = value
    }

    fun setIfEmpty(key: String, value: CacheObject<T>): Boolean {
        if (get(key) != null) {
            return false
        }
        map[key] = value
        return true
    }

    fun remove(key: String) = map.remove(key)

    fun get(key: String): CacheObject<T>? {
        val cacheObject = map[key] ?: return null
        if (cacheObject.expire.isBefore(LocalDateTime.now())) {
            remove(key)
            return null
        }
        return cacheObject
    }

    fun clear() {
        map.clear()
    }

    fun hent(nøkkel: String, populerCache: () -> T): T {
        val verdi = this.get(nøkkel)
        if (verdi == null) {
            val hentetVerdi = populerCache.invoke()
            this.set(nøkkel, CacheObject(value = hentetVerdi, expire = LocalDateTime.now().plusMinutes(30)))
            return hentetVerdi
        }
        return verdi.value
    }

}
