package no.nav.k9.los.nyoppgavestyring.mottak.omraade

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.Cache
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class OmrådeRepository(private val dataSource: DataSource) {

    private val områdeCache = Cache<String, Område>(cacheSizeLimit = null)


    fun hentOmråde(eksternId: String, tx: TransactionalSession): Område {
        return områdeCache.hent(nøkkel = eksternId) {
            tx.run(
                queryOf("select * from omrade where omrade.ekstern_id = :eksternId", mapOf("eksternId" to eksternId))
                    .map { row ->
                        Område(
                            id = row.long("id"),
                            eksternId = row.string("ekstern_id")
                        )
                    }.asSingle
            ) ?: throw IllegalArgumentException("Området finnes ikke: ${eksternId}")
        }
    }

    fun lagre(eksternId: String) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        "insert into omrade(ekstern_id) values (:ekstern_id) on conflict do nothing",
                        mapOf("ekstern_id" to eksternId)
                    ).asUpdate
                )
            }
        }
    }

    fun hent(eksternId: String): Område? {
        return using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf("select * from omrade where ekstern_id = :eksternId", mapOf("eksternId" to eksternId))
                        .map { row ->
                            Område(
                                id = row.long("id"),
                                eksternId = row.string("ekstern_id")
                            )
                        }.asSingle
                )
            }
        }
    }

    fun invaliderCache() {
        områdeCache.clear()
    }

}
