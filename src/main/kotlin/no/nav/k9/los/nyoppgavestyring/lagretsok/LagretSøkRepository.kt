package no.nav.k9.los.nyoppgavestyring.lagretsok

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import javax.sql.DataSource

class LagretSøkRepository(val dataSource: DataSource) {
    private val transactionalManager = TransactionalManager(dataSource)

    fun hent(id: Long): LagretSøk? {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                SELECT *
                FROM lagret_sok
                WHERE id = :id
            """.trimIndent(), mapOf("id" to id)
                ).map {
                    it.toLagretSøk()
                }.asSingle
            )
        }
    }


    fun opprett(lagretSøk: LagretSøk): Long {
        return transactionalManager.transaction { tx ->
            tx.updateAndReturnGeneratedKey(
                queryOf(
                    """
                    INSERT INTO lagret_sok (tittel, versjon, beskrivelse, sist_endret, query, laget_av)
                    VALUES (:tittel, :versjon, :beskrivelse, :sist_endret, :query::jsonb, :lagetAv)
                    """.trimIndent(),
                    mapOf(
                        "tittel" to lagretSøk.tittel,
                        "versjon" to lagretSøk.versjon,
                        "beskrivelse" to lagretSøk.beskrivelse,
                        "sist_endret" to lagretSøk.sistEndret,
                        "query" to LosObjectMapper.instance.writeValueAsString(lagretSøk.query),
                        "lagetAv" to lagretSøk.lagetAv,
                    )
                )
            )
        }!!
    }

    fun endre(lagretSøk: LagretSøk) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                UPDATE lagret_sok
                set tittel = :tittel, versjon = :versjon, beskrivelse = :beskrivelse, sist_endret = :sist_endret, query = :query::jsonb
                where id = :id
                """.trimIndent(),
                    mapOf(
                        "id" to lagretSøk.id,
                        "tittel" to lagretSøk.tittel,
                        "versjon" to lagretSøk.versjon,
                        "beskrivelse" to lagretSøk.beskrivelse,
                        "sist_endret" to lagretSøk.sistEndret,
                        "query" to LosObjectMapper.instance.writeValueAsString(lagretSøk.query),
                    )
                ).asUpdate
            )
        }
    }

    fun slett(lagretSøk: LagretSøk) {
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

    fun hentAlle(saksbehandler: Saksbehandler): List<LagretSøk> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                SELECT *
                FROM lagret_sok
                WHERE laget_av = :lagetAv
            """.trimIndent(), mapOf("lagetAv" to saksbehandler.id)
                ).map {
                    it.toLagretSøk()
                }.asList
            )
        }
    }
}

private fun Row.toLagretSøk(): LagretSøk {
    return LagretSøk.fraEksisterende(
        id = long("id"),
        lagetAv = long("laget_av"),
        versjon = long("versjon"),
        tittel = string("tittel"),
        beskrivelse = string("beskrivelse"),
        sistEndret = localDateTime("sist_endret"),
        query = LosObjectMapper.instance.readValue(string("query"), OppgaveQuery::class.java)
    )
}
