package no.nav.k9.los.uttrekk

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import javax.sql.DataSource

class UttrekkRepository(val dataSource: DataSource) {
    private val transactionalManager = TransactionalManager(dataSource)

    private val kolonner = """
        u.id, u.opprettet_tidspunkt, u.status, u.tittel, u.query, u.laget_av, u.lagret_sok_id,
        u.avgrensning_limit, u.avgrensning_offset, u.feilmelding, u.startet_tidspunkt, u.fullfort_tidspunkt,
        u.antall, o.ekstern_id as omrade_ekstern_id
    """.trimIndent()

    /** Uttrekk som innlogget saksbehandler eier innenfor området. Brukes fra API. */
    fun hent(område: Områder, navIdent: String, id: Long): Uttrekk? {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                    SELECT $kolonner
                    FROM uttrekk u
                    INNER JOIN omrade o ON o.id = u.omrade_id
                    INNER JOIN saksbehandler s ON s.id = u.laget_av
                    WHERE u.id = :id
                      AND o.ekstern_id = :omrade
                      AND s.navident = :navident
                    """.trimIndent(),
                    mapOf("id" to id, "omrade" to område.eksternId, "navident" to navIdent)
                ).map { it.toUttrekk() }.asSingle
            )
        }
    }

    /** Uten tilgangssjekk. Brukes kun fra jobb. */
    fun hentForJobb(id: Long): Uttrekk? {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                    SELECT $kolonner
                    FROM uttrekk u
                    INNER JOIN omrade o ON o.id = u.omrade_id
                    WHERE u.id = :id
                    """.trimIndent(),
                    mapOf("id" to id)
                ).map { it.toUttrekk() }.asSingle
            )
        }
    }

    fun hentAlle(område: Områder, navIdent: String): List<Uttrekk> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    SELECT $kolonner
                    FROM uttrekk u
                    INNER JOIN omrade o ON o.id = u.omrade_id
                    INNER JOIN saksbehandler s ON s.id = u.laget_av
                    WHERE o.ekstern_id = :omrade
                      AND s.navident = :navident
                    ORDER BY u.opprettet_tidspunkt DESC
                    """.trimIndent(),
                    mapOf("omrade" to område.eksternId, "navident" to navIdent)
                ).map { it.toUttrekk() }.asList
            )
        }
    }

    /** Uten tilgangssjekk. Brukes kun fra jobb. */
    fun hentAlleForJobb(): List<Uttrekk> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    SELECT $kolonner
                    FROM uttrekk u
                    INNER JOIN omrade o ON o.id = u.omrade_id
                    ORDER BY u.opprettet_tidspunkt DESC
                    """.trimIndent()
                ).map { it.toUttrekk() }.asList
            )
        }
    }

    fun hentForSaksbehandler(område: Områder, saksbehandlerId: Long): List<Uttrekk> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    SELECT $kolonner
                    FROM uttrekk u
                    INNER JOIN omrade o ON o.id = u.omrade_id
                    WHERE u.laget_av = :lagetAv
                      AND o.ekstern_id = :omrade
                    ORDER BY u.opprettet_tidspunkt DESC
                    """.trimIndent(),
                    mapOf("lagetAv" to saksbehandlerId, "omrade" to område.eksternId)
                ).map { it.toUttrekk() }.asList
            )
        }
    }

    fun hentResultat(område: Områder, navIdent: String, id: Long): String? {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                    SELECT u.resultat
                    FROM uttrekk u
                    INNER JOIN omrade o ON o.id = u.omrade_id
                    INNER JOIN saksbehandler s ON s.id = u.laget_av
                    WHERE u.id = :id
                      AND o.ekstern_id = :omrade
                      AND s.navident = :navident
                    """.trimIndent(),
                    mapOf("id" to id, "omrade" to område.eksternId, "navident" to navIdent)
                ).map { it.stringOrNull("resultat") }.asSingle
            )
        }
    }

    fun opprett(uttrekk: Uttrekk): Long {
        return transactionalManager.transaction { tx ->
            tx.updateAndReturnGeneratedKey(
                queryOf(
                    """
                    INSERT INTO uttrekk (opprettet_tidspunkt, status, tittel, query, type_kjoring, laget_av, lagret_sok_id, avgrensning_limit, avgrensning_offset, omrade_id)
                    VALUES (:opprettetTidspunkt, :status, :tittel, :query::jsonb, 'NY', :lagetAv, :lagretSokId, :limit, :offset, (SELECT id FROM omrade WHERE ekstern_id = :omrade))
                    """.trimIndent(),
                    mapOf(
                        "opprettetTidspunkt" to uttrekk.opprettetTidspunkt,
                        "status" to uttrekk.status.name,
                        "tittel" to uttrekk.tittel,
                        "query" to LosObjectMapper.instance.writeValueAsString(uttrekk.query),
                        "lagetAv" to uttrekk.lagetAv,
                        "lagretSokId" to uttrekk.lagretSøkId,
                        "limit" to uttrekk.limit,
                        "offset" to uttrekk.offset,
                        "omrade" to uttrekk.område.eksternId
                    )
                )
            )
        }!!
    }

    fun oppdater(uttrekk: Uttrekk, resultat: String? = null) {
        transactionalManager.transaction { tx ->
            val resultatSett = if (resultat != null) ", resultat = :resultat::jsonb" else ""
            val params = mutableMapOf(
                "id" to uttrekk.id,
                "status" to uttrekk.status.name,
                "tittel" to uttrekk.tittel,
                "feilmelding" to uttrekk.feilmelding,
                "startetTidspunkt" to uttrekk.startetTidspunkt,
                "fullfortTidspunkt" to uttrekk.fullførtTidspunkt,
                "antall" to uttrekk.antall
            )
            resultat?.let { params["resultat"] = it }

            val antallRaderOppdatert = tx.run(
                queryOf(
                    """
                    UPDATE uttrekk
                    SET status = :status, tittel = :tittel, feilmelding = :feilmelding,
                        startet_tidspunkt = :startetTidspunkt, fullfort_tidspunkt = :fullfortTidspunkt,
                        antall = :antall$resultatSett
                    WHERE id = :id
                    """.trimIndent(),
                    params
                ).asUpdate
            )
            if (antallRaderOppdatert != 1) {
                throw IllegalStateException("Feilet ved update på uttrekk. Uttrekk med id ${uttrekk.id} finnes ikke.")
            }
        }
    }

    fun slett(uttrekk: Uttrekk) {
        transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    "DELETE FROM uttrekk WHERE id = :id",
                    mapOf("id" to uttrekk.id)
                ).asUpdate
            )
        }
    }

    fun slettForLagretSøk(lagretSøkId: Long): Int {
        return transactionalManager.transaction { tx ->
            tx.run(
                queryOf(
                    """
                    DELETE FROM uttrekk
                    WHERE lagret_sok_id = :lagretSokId
                      AND status != :statusKjorer
                    """.trimIndent(),
                    mapOf("lagretSokId" to lagretSøkId, "statusKjorer" to UttrekkStatus.KJØRER.name)
                ).asUpdate
            )
        }
    }
}

private fun Row.toUttrekk(): Uttrekk {
    return Uttrekk.fraEksisterende(
        id = long("id"),
        område = Områder.fraEksternId(string("omrade_ekstern_id")),
        opprettetTidspunkt = localDateTime("opprettet_tidspunkt"),
        status = UttrekkStatus.valueOf(string("status")),
        tittel = string("tittel"),
        query = LosObjectMapper.instance.readValue(string("query"), OppgaveQuery::class.java),
        lagetAv = long("laget_av"),
        lagretSøkId = longOrNull("lagret_sok_id"),
        limit = intOrNull("avgrensning_limit"),
        offset = intOrNull("avgrensning_offset"),
        feilmelding = stringOrNull("feilmelding"),
        startetTidspunkt = localDateTimeOrNull("startet_tidspunkt"),
        fullførtTidspunkt = localDateTimeOrNull("fullfort_tidspunkt"),
        antall = intOrNull("antall")
    )
}
