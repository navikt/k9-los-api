package no.nav.k9.los.integrasjon.k9

import no.nav.k9.los.utils.Cache
import no.nav.k9.los.utils.CacheObject
import org.slf4j.LoggerFactory
import java.time.Duration
import java.time.LocalDateTime
import java.util.*

class K9SakBehandlingOppfrisketCache (private val repo : K9SakBehandlingOppfrisketRepostiory) {

    private val cacheObjectDuration = Duration.ofHours(12)

    private val hyppighetSletting = Duration.ofHours(1)
    private var sistSlettetGamleTidspunkt = LocalDateTime.MIN

    private val memoryCache by lazy { //lazy for å unngå databasekall under oppstart av applikasjonen
        val cache = Cache<UUID, Boolean>(cacheSize = 10000)
        val dbCache = repo.hentAlleOppfrisketEtter(LocalDateTime.now().minus(cacheObjectDuration))
        for (entry in dbCache) {
            cache.set(entry.behandlingUuid, CacheObject(true, entry.tidspunkt.plus(cacheObjectDuration)))
        }
        log.info("Opprettet K9SakBehandlingOppfrisketCache med ${dbCache.size} elementer fra database")
        cache
    }
    fun registrerBehandlingerOppfrisket(behandlinger: Collection<UUID>) {
        val tidspunkt = LocalDateTime.now()
        repo.registrerOppfrisket(behandlinger.map { K9sakBehandlingOppfrisketTidspunkt(it, tidspunkt) })
        behandlinger.forEach {memoryCache.set(it, CacheObject(true, tidspunkt.plus(cacheObjectDuration))) }
        log.info("La til ${behandlinger.size} behandlinger i cache")

        if (tidspunkt.isAfter(sistSlettetGamleTidspunkt.plus(hyppighetSletting))){
            repo.slettOppfrisketFør(tidspunkt.minus(cacheObjectDuration))
        }
    }

    fun containsKey(key : UUID, tidspunkt: LocalDateTime) : Boolean{
        return memoryCache.containsKey(key, tidspunkt)
    }
    companion object {

        private val log = LoggerFactory.getLogger(K9SakBehandlingOppfrisketCache::class.java)

    }

}