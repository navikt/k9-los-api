package no.nav.k9.domene.lager.oppgave.v3.datatype

import kotliquery.TransactionalSession
import kotliquery.queryOf
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class FeltdefinisjonRepository(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(FeltdefinisjonRepository::class.java)

    fun hent(område: String, tx: TransactionalSession): Feltdefinisjoner {
        val datatyper = tx.run(
            queryOf(
                """
                select * from feltdefinisjon 
                where omrade_id = (select id from omrade where omrade.ekstern_id = :omrade)
            """.trimIndent(),
                mapOf("omrade" to område)
            ).map { row ->
                Feltdefinisjon(
                    id = row.string("eksternt_navn"),
                    listetype = row.boolean("liste_type"),
                    parsesSom = row.string("parses_som"),
                    visTilBruker = true
                )
            }.asList
        )
        return Feltdefinisjoner(område, datatyper.toSet())
    }

    private fun hentOmrådeId(område: String, tx: TransactionalSession): Long {
        return tx.run(
            queryOf("select id from omrade where omrade.ekstern_id = :omrade", mapOf("omrade" to område))
                .map { row -> row.long("id") }.asSingle
        ).takeIf { id -> id != null } ?: throw IllegalArgumentException("Området finnes ikke")
    }

    fun fjern(sletteListe: Set<Feltdefinisjon>, tx: TransactionalSession) {
        sletteListe.forEach { datatype ->
            tx.run(
                queryOf(
                    """
                        delete from feltdefinisjon where eksternt_navn = :eksterntNavn""",
                    mapOf("eksterntNavn" to datatype.id)
                ).asUpdate
            )
        }
    }

    fun leggTil(leggTilListe: Set<Feltdefinisjon>, område: String, tx: TransactionalSession) {
        val områdeId = hentOmrådeId(område, tx)
        leggTilListe.forEach { datatype ->
            tx.run(
                queryOf(
                    """
                    insert into feltdefinisjon(eksternt_navn, omrade_id, liste_type, parses_som) 
                    values(:eksterntNavn, :omradeId, :listeType, :parsesSom)
                """.trimIndent(),
                    mapOf(
                        "eksterntNavn" to datatype.id,
                        "omradeId" to områdeId,
                        "listeType" to datatype.listetype,
                        "parsesSom" to datatype.parsesSom
                    )
                ).asUpdate
            )
        }
    }
}