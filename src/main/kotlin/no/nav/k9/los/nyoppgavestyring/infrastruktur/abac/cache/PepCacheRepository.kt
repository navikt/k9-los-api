package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache

import kotliquery.LoanPattern.using
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import java.time.Duration
import java.time.LocalDateTime
import javax.sql.DataSource

class PepCacheRepository(
    val dataSource: DataSource
) {

    fun lagre(cache: PepCache, tx: TransactionalSession) {
        tx.run(
            queryOf("""
                INSERT INTO OPPGAVE_PEP_CACHE (kildeomrade, ekstern_id, kode6, kode7, egen_ansatt, oppdatert) 
                VALUES(:kildeomrade, :ekstern_id, :kode6, :kode7, :egen_ansatt, :oppdatert) 
                ON CONFLICT ON CONSTRAINT pep_kildeomrade_eksternid
                DO UPDATE SET 
                    kildeomrade = :kildeomrade, 
                    ekstern_id = :ekstern_id, 
                    kode6 = :kode6, 
                    kode7 = :kode7, 
                    egen_ansatt = :egen_ansatt, 
                    oppdatert = :oppdatert
            """, mapOf(
                "kildeomrade" to cache.kildeområde,
                "ekstern_id" to cache.eksternId,
                "kode6" to cache.kode6,
                "kode7" to cache.kode7,
                "egen_ansatt" to cache.egenAnsatt,
                "oppdatert" to cache.oppdatert
            )).asUpdate
        )
    }

    fun slett(kildeområde: String, eksternId: String, tx: TransactionalSession) {
        tx.run(
            queryOf("""
                    DELETE FROM OPPGAVE_PEP_CACHE WHERE kildeomrade = :kildeomrade AND ekstern_id = :ekstern_id 
                """, mapOf(
                    "kildeomrade" to kildeområde,
                    "ekstern_id" to eksternId
                )
            ).asUpdate
        )
    }

    fun hent(kildeområde: String, eksternId: String): PepCache? {
        return using(sessionOf(dataSource)) {
            it.transaction { tx -> hent(kildeområde, eksternId, tx) }
        }
    }

    fun hent(kildeområde: String, eksternId: String, tx: TransactionalSession): PepCache? {
        return tx.run(
            queryOf("""
                    SELECT * FROM OPPGAVE_PEP_CACHE WHERE kildeomrade = :kildeomrade AND ekstern_id = :ekstern_id 
                """, mapOf(
                    "kildeomrade" to kildeområde,
                    "ekstern_id" to eksternId
                )
            ).map { it.tilPepCache() }.asSingle
        )
    }

    private fun Row.tilPepCache() = PepCache(
        kildeområde = string("kildeomrade"),
        eksternId = string("ekstern_id"),
        kode6 = boolean("kode6"),
        kode7 = boolean("kode7"),
        egenAnsatt = boolean("egen_ansatt"),
        oppdatert = localDateTime("oppdatert"),
    )
}


data class PepCache(
    val eksternId: String,
    val kildeområde: String,
    val kode6: Boolean,
    val kode7: Boolean,
    val egenAnsatt: Boolean,
    val oppdatert: LocalDateTime
) {
    fun erGyldig(maksimalAlder: Duration): Boolean {
        return oppdatert < (LocalDateTime.now() - maksimalAlder)
    }

    fun oppdater(kode6: Boolean, kode7: Boolean, egenAnsatt: Boolean): PepCache {
        return copy(
            kode6 = kode6,
            kode7 = kode7,
            egenAnsatt = egenAnsatt,
            oppdatert = LocalDateTime.now()
        )
    }

    fun måSjekkes(): Boolean {
        return kode6 || kode7 || egenAnsatt
    }
}