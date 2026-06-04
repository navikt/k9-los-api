package no.nav.k9.los.nyoppgavestyring.infrastruktur.abac.cache

import kotliquery.LoanPattern.using
import kotliquery.Row
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.util.InClauseHjelper
import no.nav.k9.los.nyoppgavestyring.oppgavemottak.Oppgavestatus
import java.time.LocalDateTime
import javax.sql.DataSource

class PepCacheRepository(
    val dataSource: DataSource
) {
    fun hentOppgaverMedStatusOgPepCacheEldreEnn(
        tidspunkt: LocalDateTime = LocalDateTime.now(),
        antall: Int = 1,
        status: Set<Oppgavestatus>,
        tx: TransactionalSession
    ): List<PepCacheInput> {
        val statusParametre = InClauseHjelper.tilParameternavn(status, "status")
        val query = """
                    SELECT o.oppgave_ekstern_id, 
                    (select ov.verdi from oppgavefelt_verdi_part ov where ov.oppgave_id = o.id AND ov.feltdefinisjon_ekstern_id = 'saksnummer' AND ov.oppgavestatus IN ($statusParametre)) as saksnummer,
                    (select ov.verdi from oppgavefelt_verdi_part ov where ov.oppgave_id = o.id AND ov.feltdefinisjon_ekstern_id = 'aktorId' AND ov.oppgavestatus IN ($statusParametre)) as aktor_id,
                    (select ov.verdi from oppgavefelt_verdi_part ov where ov.oppgave_id = o.id AND ov.feltdefinisjon_ekstern_id = 'pleietrengendeAktorId' AND ov.oppgavestatus IN ($statusParametre)) as pleietrengende_aktor_id,
                    (select ov.verdi from oppgavefelt_verdi_part ov where ov.oppgave_id = o.id AND ov.feltdefinisjon_ekstern_id = 'relatertPartAktorid' AND ov.oppgavestatus IN ($statusParametre)) as relatert_part_aktor_id
                    FROM oppgave_v3_part o 
                    LEFT JOIN OPPGAVE_PEP_CACHE opc ON (o.oppgave_ekstern_id = opc.ekstern_id AND opc.kildeomrade = 'K9')
                    WHERE o.oppgavestatus IN ($statusParametre)
                    AND (opc.oppdatert is null OR opc.oppdatert < :grense)
                    ORDER BY opc.oppdatert NULLS FIRST
                    LIMIT :limit
                """.trimIndent()
        return tx.run(
            queryOf(
                query,
                buildMap {
                    put("grense", tidspunkt)
                    put("limit", antall)
                    putAll(InClauseHjelper.parameternavnTilVerdierMap(status.map { it.kode }, "status"))
                }
            ).map { row ->
                PepCacheInput(
                    row.string("oppgave_ekstern_id"),
                    row.stringOrNull("saksnummer"),
                    listOfNotNull(
                        row.stringOrNull("aktor_id"),
                        row.stringOrNull("pleietrengende_aktor_id"),
                        row.stringOrNull("relatert_part_aktor_id")
                    )
                )
            }.asList
        )
    }

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
    fun oppdater(kode6: Boolean, kode7: Boolean, egenAnsatt: Boolean): PepCache {
        return copy(
            kode6 = kode6,
            kode7 = kode7,
            egenAnsatt = egenAnsatt,
            oppdatert = LocalDateTime.now()
        )
    }
}