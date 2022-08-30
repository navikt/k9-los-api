package no.nav.k9.domene.lager.oppgave.v3.omraade

import io.ktor.features.*
import kotliquery.TransactionalSession
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class OmrådeRepository(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(OmrådeRepository::class.java)

    fun hentOmrådeId(område: String, tx: TransactionalSession): Long {
        return tx.run(
            queryOf("select id from omrade where omrade.ekstern_id = :omrade", mapOf("omrade" to område))
                .map { row -> row.long("id") }.asSingle
        ).takeIf { id -> id != null } ?: throw IllegalArgumentException("Området finnes ikke")
    }

    fun lagre(område: String) {
        TODO()
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                tx.run(
                    queryOf(
                        "insert into omrade(ekstern_id) values (:ekstern_id)",
                        mapOf("ekstern_id" to område)
                    ).asUpdate
                )
            }
        }
    }

}
