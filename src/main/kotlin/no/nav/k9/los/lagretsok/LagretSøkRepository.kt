package no.nav.k9.los.lagretsok

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import javax.sql.DataSource

class LagretSøkRepository(val dataSource: DataSource) {
    private val transactionalManager = TransactionalManager(dataSource)

    fun hent(område: Områder, navIdent: String, id: Long): LagretSøk? {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                    SELECT o.ekstern_id AS omrade_ekstern_id, l.*
                    FROM lagret_sok l
                    INNER JOIN omrade o ON o.id = l.omrade_id
                    INNER JOIN saksbehandler s ON l.laget_av = s.id
                    WHERE l.id = :id
                      AND o.ekstern_id = :omrade
                      AND s.navident = :navident
                      AND EXISTS (select 1 from saksbehandler_omrade so where so.omrade_id = o.id and so.saksbehandler_id = s.id) 
                    """.trimIndent(),
                    mapOf("id" to id, "omrade" to område.eksternId, "navident" to navIdent)
                ).map { it.toLagretSøk() }.asSingle
            )
        }
    }

    fun opprett(lagretSøk: LagretSøk): Long {
        return transactionalManager.transaction { tx ->
            tx.updateAndReturnGeneratedKey(
                queryOf(
                    """
                    INSERT INTO lagret_sok (tittel, versjon, beskrivelse, sist_endret, query, laget_av, omrade_id)
                    VALUES (:tittel, :versjon, :beskrivelse, :sist_endret, :query::jsonb, :lagetAv, (select id from omrade where ekstern_id = :omrade))
                    """.trimIndent(),
                    mapOf(
                        "tittel" to lagretSøk.tittel,
                        "versjon" to lagretSøk.versjon,
                        "beskrivelse" to lagretSøk.beskrivelse,
                        "sist_endret" to lagretSøk.sistEndret,
                        "query" to LosObjectMapper.instance.writeValueAsString(lagretSøk.query),
                        "lagetAv" to lagretSøk.lagetAv,
                        "omrade" to lagretSøk.område.eksternId
                    )
                )
            )
        }!!
    }

    fun endre(lagretSøk: LagretSøk) {
        transactionalManager.transaction {
            val antallRaderOppdatert = it.run(
                queryOf(
                    """
                UPDATE lagret_sok
                set tittel = :tittel, versjon = :versjon, beskrivelse = :beskrivelse, sist_endret = :sist_endret, query = :query::jsonb
                where id = :id and versjon = :versjon - 1
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
            if (antallRaderOppdatert != 1) {
                throw IllegalStateException("Feilet ved update på lagret søk. Kan enten skyldes at søket er slettet, eller at versjonsnummer ikke stemmer (optimistisk lås).")
            }
        }
    }

    fun slett(lagretSøk: LagretSøk) {
        transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                    DELETE FROM lagret_sok
                    WHERE id = :id
                    """.trimIndent(),
                    mapOf("id" to lagretSøk.id)
                ).asUpdate
            )
        }
    }

    fun hentAlle(område: Områder, saksbehandler: Saksbehandler): List<LagretSøk> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    SELECT o.ekstern_id AS omrade_ekstern_id, l.*
                    FROM lagret_sok l
                    INNER JOIN omrade o ON o.id = l.omrade_id
                    WHERE l.laget_av = :lagetAv
                      AND o.ekstern_id = :omrade
                    ORDER BY l.id DESC
                    """.trimIndent(),
                    mapOf("lagetAv" to saksbehandler.id, "omrade" to område.eksternId)
                ).map { it.toLagretSøk() }.asList
            )
        }
    }
}

private fun Row.toLagretSøk(): LagretSøk {
    return LagretSøk.fraEksisterende(
        id = long("id"),
        lagetAv = long("laget_av"),
        område = Områder.fraEksternId(string("omrade_ekstern_id")),
        versjon = long("versjon"),
        tittel = string("tittel"),
        beskrivelse = string("beskrivelse"),
        sistEndret = localDateTime("sist_endret"),
        query = LosObjectMapper.instance.readValue(string("query"), OppgaveQuery::class.java)
    )
}
