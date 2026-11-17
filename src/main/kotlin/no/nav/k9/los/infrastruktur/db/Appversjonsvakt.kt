package no.nav.k9.los.infrastruktur.db

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.slf4j.LoggerFactory
import java.util.UUID
import javax.sql.DataSource

private val PROSESSVERSJON: String = System.getenv("NAIS_APP_IMAGE") ?: "lokal-${UUID.randomUUID()}"

/**
 * Hindrer at en gammel pod skriver data under rullerende deploy.
 *
 * Skrivende jobbtransaksjoner kaller [erAktiv] tidlig i transaksjonen, og avbryter når
 * en annen versjon har tatt over. [erAktiv] tar ROW SHARE-lås på tabellen ut transaksjonen.
 *
 * Ny pod kaller [taOver] ved oppstart, som tar EXCLUSIVE-lås på tabellen. Den venter på
 * pågående transaksjoner i gammel pod. Tabellåskøen er FIFO, så nye transaksjoner i gammel
 * pod stiller seg bak overtakelsen og ser ny versjon. Radlås alene holder ikke, fordi nye
 * FOR SHARE-forespørsler slipper forbi en ventende UPDATE og kan sulte ut overtakelsen.
 */
class Appversjonsvakt(
    private val dataSource: DataSource,
    val versjon: String = PROSESSVERSJON,
) {
    private val log = LoggerFactory.getLogger(Appversjonsvakt::class.java)

    fun taOver() {
        val forrige = using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(queryOf("lock table aktiv_appversjon in exclusive mode").asExecute)
                val forrige = tx.run(
                    queryOf("select versjon from aktiv_appversjon where id = 1")
                        .map { it.stringOrNull("versjon") }.asSingle
                )
                tx.run(
                    queryOf(
                        """
                            insert into aktiv_appversjon (id, versjon, oppdatert) values (1, :versjon, now())
                            on conflict (id) do update set versjon = excluded.versjon, oppdatert = excluded.oppdatert
                        """.trimIndent(),
                        mapOf("versjon" to versjon)
                    ).asUpdate
                )
                forrige
            }
        }
        log.info("Appversjon $versjon har tatt over som aktiv versjon etter $forrige")
    }

    /**
     * Som [taOver], men gjør ingenting hvis tabellen ikke finnes ennå (første deploy med vakten).
     */
    fun taOverHvisTabellFinnes(): Boolean {
        val finnes = using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf("select to_regclass('aktiv_appversjon') is not null as finnes")
                    .map { it.boolean("finnes") }.asSingle
            ) ?: false
        }
        if (!finnes) {
            log.info("Tabellen aktiv_appversjon finnes ikke ennå, hopper over overtakelse")
            return false
        }
        taOver()
        return true
    }

    /**
     * FOR SHARE gir ROW SHARE-lås på tabellen, som konkurrerer med EXCLUSIVE i [taOver].
     */
    fun erAktiv(tx: TransactionalSession): Boolean {
        val aktiv = tx.run(
            queryOf("select versjon from aktiv_appversjon where id = 1 for share")
                .map { it.stringOrNull("versjon") }.asSingle
        )
        if (aktiv == null || aktiv == versjon) return true
        log.warn("Appversjon $versjon er ikke aktiv lenger (aktiv: $aktiv). Hopper over skrivende arbeid.")
        return false
    }
}
