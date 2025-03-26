package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.refreshk9sakoppgaver

import no.nav.k9.los.eventhandler.DetaljerMetrikker
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
        val cache = Cache<UUID, Boolean>(cacheSizeLimit = null)
        val dbCache = repo.hentAlleOppfrisketEtter(LocalDateTime.now() - cacheObjectDuration)
        for (entry in dbCache.sortedBy { it.tidspunkt }) {
            cache.set(entry.behandlingUuid, CacheObject(true, entry.tidspunkt + cacheObjectDuration))
        }
        log.info("Opprettet K9SakBehandlingOppfrisketCache med ${dbCache.size} elementer fra database")
        cache
    }
    fun registrerBehandlingerOppfrisket(behandlinger: Collection<UUID>) {
        val nå = LocalDateTime.now()
        repo.registrerOppfrisket(behandlinger.map { K9sakBehandlingOppfrisketTidspunkt(it, nå) })
        memoryCache.removeExpiredObjects(nå)
        behandlinger.forEach { memoryCache.set(it, CacheObject(true, nå + cacheObjectDuration)) }
        log.info("La til ${behandlinger.size} behandlinger i cache")

        if (nå.isAfter(sistSlettetGamleTidspunkt + hyppighetSletting)){
            val slettFra = nå - cacheObjectDuration
            repo.slettOppfrisketFør(slettFra)
            sistSlettetGamleTidspunkt = nå
            log.info("Slettet elementer i cache opprettet før $slettFra")
        }
    }

    fun filterNotInCache(keys: Collection<UUID>) : List<UUID> {
        DetaljerMetrikker.observeTeller("K9SakBehandlingOppfrisketCache", "antallKall", 1)
        val nå = LocalDateTime.now()
        return keys.filterNot { containsKey(it, nå) }
    }

    private fun containsKey(key : UUID, tidspunkt: LocalDateTime) : Boolean{
        return memoryCache.containsKey(key, tidspunkt)
    }
    companion object {

        private val log = LoggerFactory.getLogger(K9SakBehandlingOppfrisketCache::class.java)

    }

}