package no.nav.k9.los.nyoppgavestyring.uttrekk

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import javax.sql.DataSource

class UttrekkRepository(val dataSource: DataSource) {
    private val transactionalManager = TransactionalManager(dataSource)

    fun hent(id: Long): Uttrekk? {
        return transactionalManager.transaction {
            it.run(
                queryOf(
                    """
                SELECT id, opprettet_tidspunkt, status, tittel, query, type_kjoring, laget_av, timeout,
                       avgrensning_limit, avgrensning_offset,
                       feilmelding, startet_tidspunkt, fullfort_tidspunkt, antall
                FROM uttrekk
                WHERE id = :id
            """.trimIndent(), mapOf("id" to id)
                ).map {
                    it.toUttrekk()
                }.asSingle
            )
        }
    }

    fun hentResultat(id: Long): String? {
        return transactionalManager.transaction {
            it.run(
                queryOf(
                    """
                SELECT resultat
                FROM uttrekk
                WHERE id = :id
            """.trimIndent(), mapOf("id" to id)
                ).map {
                    it.stringOrNull("resultat")
                }.asSingle
            )
        }
    }

    fun opprett(uttrekk: Uttrekk): Long {
        return transactionalManager.transaction { tx ->
            tx.updateAndReturnGeneratedKey(
                queryOf(
                    """
                    INSERT INTO uttrekk (opprettet_tidspunkt, status, tittel, query, type_kjoring, laget_av, timeout, avgrensning_limit, avgrensning_offset)
                    VALUES (:opprettetTidspunkt, :status, :tittel, :query::jsonb, :typeKjoring, :lagetAv, :timeout, :limit, :offset)
                    """.trimIndent(),
                    mapOf(
                        "opprettetTidspunkt" to uttrekk.opprettetTidspunkt,
                        "status" to uttrekk.status.name,
                        "tittel" to uttrekk.tittel,
                        "query" to LosObjectMapper.instance.writeValueAsString(uttrekk.query),
                        "typeKjoring" to uttrekk.typeKjøring.name,
                        "lagetAv" to uttrekk.lagetAv,
                        "timeout" to uttrekk.timeout,
                        "limit" to uttrekk.limit,
                        "offset" to uttrekk.offset
                    )
                )
            )
        }!!
    }

    fun oppdater(uttrekk: Uttrekk, resultat: String? = null) {
        transactionalManager.transaction {
            val sql = if (resultat != null) {
                """
                UPDATE uttrekk
                SET status = :status, tittel = :tittel, resultat = :resultat::jsonb, feilmelding = :feilmelding, startet_tidspunkt = :startetTidspunkt, fullfort_tidspunkt = :fullfortTidspunkt, antall = :antall
                WHERE id = :id
                """.trimIndent()
            } else {
                """
                UPDATE uttrekk
                SET status = :status, tittel = :tittel, feilmelding = :feilmelding, startet_tidspunkt = :startetTidspunkt, fullfort_tidspunkt = :fullfortTidspunkt, antall = :antall
                WHERE id = :id
                """.trimIndent()
            }

            val params = mutableMapOf(
                "id" to uttrekk.id,
                "status" to uttrekk.status.name,
                "tittel" to uttrekk.tittel,
                "feilmelding" to uttrekk.feilmelding,
                "startetTidspunkt" to uttrekk.startetTidspunkt,
                "fullfortTidspunkt" to uttrekk.fullførtTidspunkt,
                "antall" to uttrekk.antall
            )
            if (resultat != null) {
                params["resultat"] = resultat
            }

            val antallRaderOppdatert = it.run(queryOf(sql, params).asUpdate)
            if (antallRaderOppdatert != 1) {
                throw IllegalStateException("Feilet ved update på uttrekk. Uttrekk med id ${uttrekk.id} finnes ikke.")
            }
        }
    }

    fun slett(id: Long) {
        transactionalManager.transaction {
            it.run(
                queryOf(
                    """
                DELETE FROM uttrekk
                WHERE id = :id
            """.trimIndent(), mapOf("id" to id)
                ).asUpdate
            )
        }
    }

    fun hentAlle(): List<Uttrekk> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                SELECT id, opprettet_tidspunkt, status, tittel, query, type_kjoring, laget_av, timeout,
                       avgrensning_limit, avgrensning_offset,
                       feilmelding, startet_tidspunkt, fullfort_tidspunkt, antall
                FROM uttrekk
                ORDER BY opprettet_tidspunkt DESC
            """.trimIndent()
                ).map {
                    it.toUttrekk()
                }.asList
            )
        }
    }

    fun hentForSaksbehandler(saksbehandlerId: Long): List<Uttrekk> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                SELECT id, opprettet_tidspunkt, status, tittel, query, type_kjoring, laget_av, timeout,
                       avgrensning_limit, avgrensning_offset,
                       feilmelding, startet_tidspunkt, fullfort_tidspunkt, antall
                FROM uttrekk
                WHERE laget_av = :lagetAv
                ORDER BY opprettet_tidspunkt DESC
            """.trimIndent(), mapOf("lagetAv" to saksbehandlerId)
                ).map {
                    it.toUttrekk()
                }.asList
            )
        }
    }
}

private fun Row.toUttrekk(): Uttrekk {
    return Uttrekk.fraEksisterende(
        id = long("id"),
        opprettetTidspunkt = localDateTime("opprettet_tidspunkt"),
        status = UttrekkStatus.valueOf(string("status")),
        tittel = string("tittel"),
        query = LosObjectMapper.instance.readValue(string("query"), OppgaveQuery::class.java),
        typeKjoring = TypeKjøring.valueOf(string("type_kjoring")),
        lagetAv = long("laget_av"),
        timeout = int("timeout"),
        limit = intOrNull("avgrensning_limit"),
        offset = intOrNull("avgrensning_offset"),
        feilmelding = stringOrNull("feilmelding"),
        startetTidspunkt = localDateTimeOrNull("startet_tidspunkt"),
        fullførtTidspunkt = localDateTimeOrNull("fullfort_tidspunkt"),
        antall = intOrNull("antall")
    )
}
