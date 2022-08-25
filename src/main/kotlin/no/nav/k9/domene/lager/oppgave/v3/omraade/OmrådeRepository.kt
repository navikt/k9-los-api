package no.nav.k9.domene.lager.oppgave.v3.omraade

import io.ktor.features.*
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.slf4j.LoggerFactory
import javax.sql.DataSource

class OmrådeRepository(private val dataSource: DataSource) {

    private val log = LoggerFactory.getLogger(OmrådeRepository::class.java)

    fun lagre(område: String) {
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

    fun hent(navn: String): Long {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "select id from omrade where ekstern_id = :navn",
                    mapOf("navn" to navn)
                ).map { row ->
                    row.long("id")
                }.asSingle
            ).takeIf { it != null } ?: throw NotFoundException("Fant ikke område")
        }
    }

    /*
    fun lagre(datatyper: Set<Datatype>) {
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                val områdeId = områdeRepository.hent(datatyper.område)

                datatyper.datatyper.forEach { datatype ->
                    tx.run(
                        queryOf(
                            """
                            insert into datatype(ekstern_id, omrade_id, liste_type, implementasjonstype)
                            values(:ekstern_id, :omrade_id, :liste_type, :implementasjonstype)
                            """.trimIndent(),
                            mapOf(
                                "ekstern_id" to datatype.id,
                                "omrade_id" to områdeId,
                                "liste_type" to datatype.listetype,
                                "implementasjonstype" to datatype.implementasjonstype
                            )
                        ).asUpdate
                    )
                }
            }
        }
    }
     */

    /*
    fun lagre(oppgavetyper: Oppgavetyper) {
        using(sessionOf(dataSource), ) { session ->
            session.transaction { tx ->
                val områdeId = områdeRepository.hent(oppgavetyper.område)

                oppgavetyper.oppgavetyper.forEach{ oppgavetype ->
                    val oppgaveTypeId = tx.updateAndReturnGeneratedKey(
                        queryOf("""
                        insert into oppgavetype(ekstern_id, omrade_id, definisjonskilde)
                        values(:ekstern_id, :omrade_id, :definisjonskilde)
                        """.trimIndent(),
                            mapOf(
                                "ekstern_id" to oppgavetype.id,
                                "omrade_id" to områdeId,
                                "definisjonskilde" to oppgavetyper.definisjonskilde
                            )
                        )
                    )

                    oppgavetype.oppgavefelter.forEach { oppgavefelt ->
                        tx.run(
                            queryOf("""
                                insert into oppgavefelt()
                            """.trimIndent(),
                            mapOf()
                            ).asUpdate
                        )
                    }
                }
            }
        }
    }
    */
}
