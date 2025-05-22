package no.nav.k9.los.nyoppgavestyring.lagretsok

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import javax.sql.DataSource

class LagretSøkRepository(val dataSource: DataSource) {
    private val transactionalManager = TransactionalManager(dataSource)

    fun opprettLagretSøk(lagretSøk: LagretSøk): Long {
        return transactionalManager.transaction { tx ->
                tx.updateAndReturnGeneratedKey(
                    queryOf(
                        """
                INSERT INTO lagret_sok (tittel, versjon, beskrivelse, query, laget_av)
                VALUES (:tittel, :versjon, :beskrivelse, :query::jsonb, :lagetAv)
                """.trimIndent(),
                        mapOf(
                            "tittel" to lagretSøk.tittel,
                            "versjon" to lagretSøk.versjon,
                            "beskrivelse" to lagretSøk.beskrivelse,
                            "query" to LosObjectMapper.instance.writeValueAsString(lagretSøk.query),
                            "lagetAv" to lagretSøk.lagetAv,
                        )
                    )
                )
        }!!
    }

    fun endreLagretSøk(lagretSøk: LagretSøk) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                UPDATE lagret_sok
                set tittel = :tittel, versjon = :versjon, beskrivelse = :beskrivelse, query = :query
                where id = :id
                """.trimIndent(),
                    mapOf(
                        "id" to lagretSøk.id,
                        "tittel" to lagretSøk.tittel,
                        "versjon" to lagretSøk.versjon,
                        "beskrivelse" to lagretSøk.beskrivelse,
                        "query" to LosObjectMapper.instance.writeValueAsString(lagretSøk.query),
                    )
                ).asUpdate
            )
        }
    }

    fun hentLagretSøk(id: Long): LagretSøk? {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                SELECT *
                FROM lagret_sok
                WHERE id = :id
            """.trimIndent(), mapOf("id" to id)
                ).map { row ->
                    LagretSøk.fraDatabasen(
                        id = row.long("id"),
                        tittel = row.string("tittel"),
                        versjon = row.long("versjon"),
                        beskrivelse = row.string("beskrivelse"),
                        query = LosObjectMapper.instance.readValue(row.string("query"), OppgaveQuery::class.java),
                        lagetAv = row.long("laget_av"),
                    )
                }
                    .asSingle
            )
        }
    }

    fun slettLagretSøk(lagretSøk: LagretSøk) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                DELETE FROM lagret_sok
                WHERE id = :id
            """.trimIndent(), mapOf("id" to lagretSøk.id)
                ).asUpdate
            )
        }
    }
}