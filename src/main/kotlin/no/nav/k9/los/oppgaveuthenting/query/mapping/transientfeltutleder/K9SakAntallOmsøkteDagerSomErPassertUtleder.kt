package no.nav.k9.los.oppgaveuthenting.query.mapping.transientfeltutleder

import no.nav.k9.los.oppgaveuthenting.query.db.OmrådeOgKode
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * Teller antall dager fra relevanteSøknadsperioder som er før nåværende dato.
 *
 * Periodene er lagret i ISO 8601 intervallformat: YYYY-MM-DD/YYYY-MM-DD.
 * For hver periode telles dager fra fom til og med min(tom, i går).
 */
class K9SakAntallOmsøkteDagerSomErPassertUtleder : TransientFeltutleder {

    companion object {
        private val PERIODER_FELT = OmrådeOgKode("K9", "relevanteSøknadsperioder")

        fun antallDagerFørDato(perioder: List<String>, dato: LocalDate): Long {
            return perioder.sumOf { periodeStr ->
                val deler = periodeStr.split("/")
                val fom = LocalDate.parse(deler[0])
                val tom = LocalDate.parse(deler[1])
                val effektivTom = minOf(tom, dato.minusDays(1))
                if (effektivTom >= fom) {
                    ChronoUnit.DAYS.between(fom, effektivTom) + 1
                } else {
                    0L
                }
            }
        }
    }

    override fun hentVerdi(input: HentVerdiInput): List<String> {
        val perioder = input.oppgave.hentListeverdi(PERIODER_FELT.kode)
        val antallDager = antallDagerFørDato(perioder, input.now.toLocalDate())
        return listOf(antallDager.toString())
    }

    override fun where(input: WhereInput): SqlMedParams {
        val subquery = antallPasserteDagerSql(input.spørringstrategi.verditabell, input.now)
        val query = "(${subquery.query}) ${input.operator.sql} (:inputVerdi)"
        val inputVerdi = when (input.feltverdi) {
            is String -> (input.feltverdi as String).toLong()
            is Number -> (input.feltverdi as Number).toLong()
            else -> input.feltverdi
        }
        return SqlMedParams(query, subquery.queryParams + mapOf("inputVerdi" to inputVerdi))
    }

    override fun orderBy(input: OrderByInput): SqlMedParams {
        val order = if (input.økende) "ASC" else "DESC"
        val subquery = antallPasserteDagerSql(input.spørringstrategi.verditabell, input.now)
        return SqlMedParams("(${subquery.query}) $order", subquery.queryParams)
    }

    override fun select(input: SelectInput): SqlMedParams {
        val subquery = antallPasserteDagerSql(input.spørringstrategi.verditabell, input.now)
        return SqlMedParams("CAST((${subquery.query}) AS text)", subquery.queryParams)
    }

    private fun antallPasserteDagerSql(verditabell: String, now: LocalDateTime): SqlMedParams {
        // Perioder er lagret som 'YYYY-MM-DD/YYYY-MM-DD'
        // Vi parser fom og tom fra strengen, kapper tom til i går, og teller dager
        val query = """
            COALESCE((
                SELECT SUM(
                    GREATEST(
                        LEAST(CAST(split_part(ov.verdi, '/', 2) AS date), CAST(:now AS date) - 1)
                        - CAST(split_part(ov.verdi, '/', 1) AS date)
                        + 1,
                        0
                    )
                )
                FROM $verditabell ov
                WHERE ov.oppgave_id = o.id
                  AND ov.feltdefinisjon_ekstern_id = '${PERIODER_FELT.kode}'
                  AND ov.verdi IS NOT NULL
            ), 0)
        """.trimIndent()
        return SqlMedParams(query, mapOf("now" to now))
    }
}

