package no.nav.k9.los.nyoppgavestyring.uttrekk

import kotliquery.Row
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import javax.sql.DataSource

class UttrekkRepository(val dataSource: DataSource) {
    private val transactionalManager = TransactionalManager(dataSource)

    fun hent(id: Long): Uttrekk? {
        return transactionalManager.transaction {
            it.run(
                queryOf(
                    """
                SELECT *
                FROM uttrekk
                WHERE id = :id
            """.trimIndent(), mapOf("id" to id)
                ).map {
                    it.toUttrekk()
                }.asSingle
            )
        }
    }

    fun opprett(uttrekk: Uttrekk): Long {
        return transactionalManager.transaction { tx ->
            tx.updateAndReturnGeneratedKey(
                queryOf(
                    """
                    INSERT INTO uttrekk (opprettet_tidspunkt, status, lagret_sok, kjoreplan, type_kjoring, resultat)
                    VALUES (:opprettetTidspunkt, :status, :lagretSok, :kjoreplan, :typeKjoring, :resultat::jsonb)
                    """.trimIndent(),
                    mapOf(
                        "opprettetTidspunkt" to uttrekk.opprettetTidspunkt,
                        "status" to uttrekk.status.name,
                        "lagretSok" to uttrekk.lagretSøkId,
                        "kjoreplan" to uttrekk.kjøreplan,
                        "typeKjoring" to uttrekk.typeKjøring.name,
                        "resultat" to uttrekk.resultat
                    )
                )
            )
        }!!
    }

    fun oppdater(uttrekk: Uttrekk) {
        transactionalManager.transaction {
            val antallRaderOppdatert = it.run(
                queryOf(
                    """
                UPDATE uttrekk
                SET status = :status, resultat = :resultat::jsonb, feilmelding = :feilmelding, startet_tidspunkt = :startetTidspunkt, fullfort_tidspunkt = :fullfortTidspunkt
                WHERE id = :id
                """.trimIndent(),
                    mapOf(
                        "id" to uttrekk.id,
                        "status" to uttrekk.status.name,
                        "resultat" to uttrekk.resultat,
                        "feilmelding" to uttrekk.feilmelding,
                        "startetTidspunkt" to uttrekk.startetTidspunkt,
                        "fullfortTidspunkt" to uttrekk.fullførtTidspunkt
                    )
                ).asUpdate
            )
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
                SELECT *
                FROM uttrekk
                ORDER BY opprettet_tidspunkt DESC
            """.trimIndent()
                ).map {
                    it.toUttrekk()
                }.asList
            )
        }
    }

    fun hentForLagretSok(lagretSokId: Long): List<Uttrekk> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                SELECT *
                FROM uttrekk
                WHERE lagret_sok = :lagretSokId
                ORDER BY opprettet_tidspunkt DESC
            """.trimIndent(), mapOf("lagretSokId" to lagretSokId)
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
        lagretSokId = long("lagret_sok"),
        kjoreplan = stringOrNull("kjoreplan"),
        typeKjoring = TypeKjøring.valueOf(string("type_kjoring")),
        resultat = stringOrNull("resultat"),
        feilmelding = stringOrNull("feilmelding"),
        startetTidspunkt = localDateTimeOrNull("startet_tidspunkt"),
        fullførtTidspunkt = localDateTimeOrNull("fullfort_tidspunkt")
    )
}
