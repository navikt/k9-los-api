package no.nav.k9.los.lagretsok

import kotliquery.*
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import javax.sql.DataSource

class LagretSøkRepository(val dataSource: DataSource) {
    private val transactionalManager = TransactionalManager(dataSource)

    fun hent(id: Long): LagretSøk? {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                SELECT omrade.ekstern_id as omrade_ekstern_id, lagret_sok.*
                FROM lagret_sok
                INNER JOIN omrade on omrade.id = lagret_sok.omrade_id
                WHERE lagret_sok.id = :id
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

    context(tx: TransactionalSession)
    fun slett(lagretSøk: LagretSøk) {
        tx.run(
            queryOf(
                """
                DELETE FROM lagret_sok
                WHERE id = :id
            """.trimIndent(), mapOf("id" to lagretSøk.id)
            ).asUpdate
        )
    }

    fun hentAlle(saksbehandler: Saksbehandler, område: Områder): List<LagretSøk> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                SELECT omrade.ekstern_id as omrade_ekstern_id, lagret_sok.*
                FROM lagret_sok
                INNER JOIN omrade on omrade.ekstern_id = :omrade
                WHERE laget_av = :lagetAv AND omrade.ekstern_id = :omrade
                ORDER BY lagret_sok.id DESC
            """.trimIndent(), mapOf("lagetAv" to saksbehandler.id, "omrade" to område.eksternId)
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
        område = Områder.fraEksternId(string("omrade_ekstern_id")),
        versjon = long("versjon"),
        tittel = string("tittel"),
        beskrivelse = string("beskrivelse"),
        sistEndret = localDateTime("sist_endret"),
        query = LosObjectMapper.instance.readValue(string("query"), OppgaveQuery::class.java)
    )
}
