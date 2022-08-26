package no.nav.k9.domene.lager.oppgave.v3.datatype

import kotliquery.TransactionalSession
import kotliquery.queryOf
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class DatatypeRepository(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(DatatypeRepository::class.java)

    fun hent(område: String, tx: TransactionalSession): Datatyper {
        val datatyper = tx.run(
            queryOf(
                """
                select * from datatype 
                where omrade_id = (select id from omrade where omrade.ekstern_id = :omrade)
            """.trimIndent(),
                mapOf("omrade" to område)
            ).map { row ->
                Datatype(
                    id = row.string("ekstern_id"),
                    listetype = row.boolean("liste_type"),
                    implementasjonstype = row.string("implementasjonstype"),
                    visTilBruker = true
                )
            }.asList
        )
        return Datatyper(område, datatyper.toSet())
    }

    private fun hentOmrådeId(område: String, tx: TransactionalSession): Long {
        return tx.run(
            queryOf("select id from omrade where omrade.ekstern_id = :omrade", mapOf("omrade" to område))
                .map { row -> row.long("id") }.asSingle
        ).takeIf { id -> id != null } ?: throw IllegalArgumentException("Området finnes ikke")
    }

    fun fjern(sletteListe: Set<Datatype>, tx: TransactionalSession) {
        sletteListe.forEach { datatype ->
            tx.run {
                queryOf(
                    "delete from datatype where ekstern_id = :eksternId",
                    mapOf("eksternId" to datatype.id)
                )
            }.asUpdate
        }
    }

    fun leggTil(leggTilListe: Set<Datatype>, område: String, tx: TransactionalSession) {
        val områdeId = hentOmrådeId(område, tx)
        leggTilListe.forEach { datatype ->
            tx.run(
                queryOf(
                    """
                    insert into datatype(ekstern_id, omrade_id, liste_type, implementasjonstype) 
                    values(:eksternId, :omradeId, :listeType, :implementasjonstype)
                """.trimIndent(),
                    mapOf(
                        "eksternId" to datatype.id,
                        "omradeId" to områdeId,
                        "listeType" to datatype.listetype,
                        "implementasjonstype" to datatype.implementasjonstype
                    )
                ).asUpdate
            )
        }
    }
}