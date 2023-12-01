package no.nav.k9.los.nyoppgavestyring.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode
import no.nav.k9.los.spi.felter.*
import org.postgresql.util.PGInterval
import java.time.LocalDateTime

abstract class LøpendeDurationTransientFeltutleder(
    /**
     * Felter med durations som alltid skal summeres opp.
     *
     * ADVARSEL: Disse verdiene legges inn direkte inn i SQL uten
     *           uten prepared statement.
     */
    val durationfelter: List<OmrådeOgKode> = listOf(),

    /**
     * Felter som gjør at vi teller tid fra opprettet oppgaveversjon
     * til nå-tid. Det er tilstrekkelig at minst ett felt er true.
     *
     * ADVARSEL: Disse verdiene legges inn direkte inn i SQL uten
     *           uten prepared statement.
     */
    val løpendetidfelter: List<OmrådeOgKode> = listOf()
): TransientFeltutleder{

    private fun sumLøpendeDuration(now: LocalDateTime): SqlMedParams {
        val minstEtDurationFeltSql = sqlVelgFelt(durationfelter)
        val løpendetidfelterSql = sqlVelgFelt(løpendetidfelter)

        val query = """
                (
                    COALESCE((
                        SELECT SUM(CAST(ov.verdi AS interval)) 
                        FROM Oppgavefelt_verdi ov INNER JOIN Oppgavefelt f ON (
                          f.id = ov.oppgavefelt_id
                        ) INNER JOIN Feltdefinisjon fd ON (
                          fd.id = f.feltdefinisjon_id
                        ) INNER JOIN Omrade fo ON (
                          fo.id = fd.omrade_id
                        )
                        WHERE ov.oppgave_id = o.id
                          AND $minstEtDurationFeltSql
                        )
                    ), INTERVAL '0 days')
                    +
                    COALESCE((
                        SELECT (:now - o.endret_tidspunkt)
                        WHERE EXISTS (
                            SELECT 'Y'
                            FROM Oppgavefelt_verdi ov INNER JOIN Oppgavefelt f ON (
                              f.id = ov.oppgavefelt_id
                            ) INNER JOIN Feltdefinisjon fd ON (
                              fd.id = f.feltdefinisjon_id
                            ) INNER JOIN Omrade fo ON (
                              fo.id = fd.omrade_id
                            )
                            WHERE ov.oppgave_id = o.id
                              AND ov.verdi = 'true'
                              AND $løpendetidfelterSql
                            )
                        )
                    ), INTERVAL '0 days')
                )
            """.trimIndent()

        return SqlMedParams(query, mutableMapOf("now" to now))
    }

    private fun sqlVelgFelt(felter: List<OmrådeOgKode>): String {
        return "(" + felter.map {
            "fo.ekstern_id = ${it.område} AND fd.ekstern_id = ${it.kode}"
        }.reduce { ok1, ok2 ->
            "$ok1 OR $ok2"
        } + ")"
    }

    override fun hentVerdi(input: HentVerdiInput): List<String> {
        val verdi = input.oppgave.hentVerdi("K9", "løsbartAksjonspunkt")
        val erTilBeslutter = (verdi != null && verdi == "5016").toString()
        return listOf(erTilBeslutter)
    }

    override fun where(input: WhereInput): SqlMedParams {
        val sumLøpendeDuration = sumLøpendeDuration(input.now)
        val query = """
                ${sumLøpendeDuration.query} ${input.operator.sql} (:inputVerdi)
            """.trimIndent()

        val inputVerdi = try {
            PGInterval(input.feltverdi as String)
        } catch (e: Exception) { null }

        val params = mapOf("inputVerdi" to inputVerdi)

        return SqlMedParams(query, (sumLøpendeDuration.queryParams + params))
    }

    override fun orderBy(input: OrderByInput): SqlMedParams {
        val order =  if (input.økende) "ASC" else "DESC"
        val sumLøpendeDuration = sumLøpendeDuration(input.now)
        val query = """
                ${sumLøpendeDuration.query} $order
            """.trimIndent()

        return SqlMedParams(query, sumLøpendeDuration.queryParams)
    }
}