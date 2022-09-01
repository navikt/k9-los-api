package no.nav.k9.nyoppgavestyring.omraade

import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class OmrådeRepository(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(OmrådeRepository::class.java)

    fun hentOmråde(eksternId: String, tx: TransactionalSession): Område {
        return tx.run(
            queryOf("select * from omrade where omrade.ekstern_id = :eksternId", mapOf("eksternId" to eksternId))
                .map { row ->
                    Område(
                        id = row.long("id"),
                        eksternId = row.string("ekstern_id")
                    )
                }.asSingle
        ) ?: throw IllegalArgumentException("Området finnes ikke")
    }

    fun lagre(område: String) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        "insert into omrade(ekstern_id) values (:ekstern_id) on conflict do nothing",
                        mapOf("ekstern_id" to område)
                    ).asUpdate
                )
            }
        }
    }

}
