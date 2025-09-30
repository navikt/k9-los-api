package no.nav.k9.los.nyoppgavestyring.query.mapping.transientfeltutleder

import no.nav.k9.los.nyoppgavestyring.spi.felter.HentVerdiInput
import no.nav.k9.los.nyoppgavestyring.spi.felter.OrderByInput
import no.nav.k9.los.nyoppgavestyring.spi.felter.SqlMedParams
import no.nav.k9.los.nyoppgavestyring.spi.felter.TransientFeltutleder
import no.nav.k9.los.nyoppgavestyring.spi.felter.WhereInput
import no.nav.k9.los.nyoppgavestyring.query.db.OmrådeOgKode
import no.nav.k9.los.nyoppgavestyring.query.db.Spørringstrategi
import org.postgresql.util.PGInterval
import java.time.Duration
import java.time.LocalDateTime

abstract class LøpendeDurationTransientFeltutleder(
    /**
     * Felter med durations som alltid skal summeres opp.
     *
     * ADVARSEL: Disse verdiene legges inn direkte inn i SQL uten
     *           prepared statement.
     */
    val durationfelter: List<OmrådeOgKode> = listOf(),

    /**
     * Felter som gjør at vi teller tid fra opprettet oppgaveversjon
     * til nå-tid. Det er tilstrekkelig at minst ett felt er true.
     *
     * ADVARSEL: Disse verdiene legges inn direkte inn i SQL uten
     *           prepared statement.
     */
    val løpendeTidHvisTrueFelter: List<OmrådeOgKode> = listOf(),

    /**
     * Felter der vi regner duration ved å ta nå-tid og trekke fra
     * angitt felt.
     *
     * ADVARSEL: Disse verdiene legges inn direkte inn i SQL uten
     *           prepared statement.
     */
    val løpendeTidFelter: List<OmrådeOgKode> = listOf(),
): TransientFeltutleder {

    private fun sumLøpendeDuration(tabellStrategi: Spørringstrategi, now: LocalDateTime): SqlMedParams {
        val sumDurationFelter = sumDurationfelter(tabellStrategi)
        val sumLøpendTidHvisTruefelter = sumLøpendeTidHvisTruefelter(tabellStrategi, now)
        val sumLøpendetidfelter = sumLøpendetidfelter(tabellStrategi, now)

        val sqlMedParams = listOfNotNull(sumDurationFelter, sumLøpendTidHvisTruefelter, sumLøpendetidfelter).reduce { a, b ->
            SqlMedParams("${a.query} + ${b.query}", a.queryParams + b.queryParams)
        }

        return SqlMedParams("(${sqlMedParams.query})", sqlMedParams.queryParams)
    }

    private fun sumDurationfelter(tabellStrategi: Spørringstrategi): SqlMedParams? {
        if (durationfelter.isEmpty()) {
            return null
        }

        val minstEttDurationFeltSql = sqlVelgFelt(durationfelter, tabellStrategi)
        val query = """
            COALESCE((
                SELECT SUM(CAST(ov.verdi AS interval)) 
                FROM ${tabellStrategi.verditabell} ov 
                WHERE ov.oppgave_id = o.id
                  AND $minstEttDurationFeltSql
            ), INTERVAL '0 days')
            """.trimIndent()
        return SqlMedParams(query, mapOf())
    }

    private fun sumLøpendeTidHvisTruefelter(spørringstrategi: Spørringstrategi, now: LocalDateTime): SqlMedParams? {
        if (løpendeTidHvisTrueFelter.isEmpty()) {
            return null
        }

        val løpendeOppgavetidHvisTrueSql = sqlVelgFelt(løpendeTidHvisTrueFelter, spørringstrategi)
        val query = """
            COALESCE((
                SELECT (:now - o.endret_tidspunkt)
                WHERE EXISTS (
                    SELECT 'Y'
                    FROM ${spørringstrategi.verditabell} ov 
                    WHERE ov.oppgave_id = o.id
                      AND ov.verdi = 'true'
                      AND $løpendeOppgavetidHvisTrueSql
                )
            ), INTERVAL '0 days')
            """.trimIndent()

        return SqlMedParams(query, mapOf("now" to now))
    }

    private fun sumLøpendetidfelter(spørringstrategi: Spørringstrategi, now: LocalDateTime): SqlMedParams? {
        if (løpendeTidFelter.isEmpty()) {
            return null
        }

        val query = "(" + løpendeTidFelter.map { områdeOgKode ->
            """(
                   (
                       :now
                   ) - (
                       SELECT CAST(ov.verdi AS timestamp)
                       FROM ${spørringstrategi.verditabell} ov 
                       WHERE ov.oppgave_id = o.id
                         AND ${områdeOgKodeSql(områdeOgKode, spørringstrategi)}
                   )
               )
            """.trimIndent()
        }.reduce { a,b ->
            "$a + $b"
        } + ")"

        return SqlMedParams(query, mapOf("now" to now))
    }

    private fun sqlVelgFelt(felter: List<OmrådeOgKode>, spørringstrategi: Spørringstrategi): String {
        return "(" + felter.joinToString(" OR ") {
            områdeOgKodeSql(it, spørringstrategi)
        } + ")"
    }

    private fun områdeOgKodeSql(områdeOgKode: OmrådeOgKode, spørringstrategi: Spørringstrategi) =
        when (spørringstrategi) {
            Spørringstrategi.PARTISJONERT -> "ov.feltdefinisjon_ekstern_id = '${områdeOgKode.kode}'"
            Spørringstrategi.AKTIV -> "ov.omrade_ekstern_id = '${områdeOgKode.område}' AND ov.feltdefinisjon_ekstern_id = '${områdeOgKode.kode}'"
        }

    override fun hentVerdi(input: HentVerdiInput): List<String> {
        var løpendeDuration = Duration.ZERO
        if (durationfelter.isNotEmpty()) {
            løpendeDuration += durationfelter.map { områdeOgKode ->
                val verdi = input.oppgave.hentVerdi(områdeOgKode.område!!, områdeOgKode.kode)
                verdi?.let { Duration.parse(verdi) } ?: Duration.ZERO
            }.reduce { d1, d2 -> d1 + d2 }
        }

        val skalTelleTidPåSisteVersjon = løpendeTidHvisTrueFelter.any { områdeOgKode ->
            input.oppgave.hentVerdi(områdeOgKode.område!!, områdeOgKode.kode)?.let { it == "true" } ?: false
        }
        if (skalTelleTidPåSisteVersjon) {
            løpendeDuration += Duration.between(input.oppgave.endretTidspunkt, input.now)
        }

        if (løpendeTidFelter.isNotEmpty()) {
            løpendeDuration += løpendeTidFelter.map { områdeOgKode ->
                val verdi = input.oppgave.hentVerdi(områdeOgKode.område!!, områdeOgKode.kode)
                verdi?.let { Duration.between(LocalDateTime.parse(verdi), input.now) } ?: Duration.ZERO
            }.reduce { d1, d2 -> d1 + d2 }
        }

        return listOf(løpendeDuration.toString())
    }

    override fun where(input: WhereInput): SqlMedParams {
        val sumLøpendeDuration = sumLøpendeDuration(input.spørringstrategi, input.now)
        val query = """
                ${sumLøpendeDuration.query} ${input.operator.sql} (:inputVerdi)
            """.trimIndent()

        val inputVerdi = input.feltverdi as PGInterval

        val params = mapOf("inputVerdi" to inputVerdi)
        return SqlMedParams(query, (sumLøpendeDuration.queryParams + params))
    }

    override fun orderBy(input: OrderByInput): SqlMedParams {
        val order =  if (input.økende) "ASC" else "DESC"
        val sumLøpendeDuration = sumLøpendeDuration(input.spørringstrategi, input.now)
        val query = """
                ${sumLøpendeDuration.query} $order
            """.trimIndent()

        return SqlMedParams(query, sumLøpendeDuration.queryParams)
    }
}