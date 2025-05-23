package no.nav.k9.los.nyoppgavestyring.lagretsok

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
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
                ).map(LagretSøk::fraRow).asSingle
            )
        }
    }


    fun opprett(lagretSøk: LagretSøk): Long {
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

    fun endre(lagretSøk: LagretSøk) {
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                UPDATE lagret_sok
                set tittel = :tittel, versjon = :versjon, beskrivelse = :beskrivelse, query = :query::jsonb
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
                ).map(LagretSøk::fraRow).asList
            )
        }
    }
}