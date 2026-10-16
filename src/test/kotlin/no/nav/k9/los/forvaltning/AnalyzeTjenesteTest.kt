package no.nav.k9.los.forvaltning

import no.nav.k9.los.AbstractPostgresTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using

class AnalyzeTjenesteTest : AbstractPostgresTest() {

    private fun tjeneste() = AnalyzeTjeneste(
        dataSource = dataSource,
        // Testcontaineren eier tabellene selv, så ingen SET ROLE er nødvendig.
        adminTilkobling = { block -> dataSource.connection.use { block(it) } },
    )

    @Test
    fun `avviser tabell utenfor hvitelisten`() {
        val resultat = tjeneste().startAnalyze("saksbehandler")

        val ugyldig = assertInstanceOf(AnalyzeStartResultat.UgyldigTabell::class.java, resultat)
        assertTrue(ugyldig.begrunnelse.contains("hvitelisten"))
    }

    @Test
    fun `avviser forsok pa sql-injeksjon i tabellnavnet`() {
        val resultat = tjeneste().startAnalyze("oppgave_v3_part; drop table saksbehandler; --_part")

        assertInstanceOf(AnalyzeStartResultat.UgyldigTabell::class.java, resultat)
    }

    @Test
    fun `avviser tabell som matcher hvitelisten men ikke finnes`() {
        val resultat = tjeneste().startAnalyze("oppgave_v3_finnes_ikke_part")

        val ugyldig = assertInstanceOf(AnalyzeStartResultat.UgyldigTabell::class.java, resultat)
        assertTrue(ugyldig.begrunnelse.contains("Fant ingen tabell"))
    }

    @Test
    fun `analyserer en enkeltpartisjon`() {
        leggInnOppgaverader(antall = 300)
        val før = sisteAnalyze("oppgave_v3_aapen_venter_uavklart_part")

        val resultat = tjeneste().startAnalyze("oppgave_v3_aapen_venter_uavklart_part")

        val startet = assertInstanceOf(AnalyzeStartResultat.Startet::class.java, resultat)
        assertEquals("oppgave_v3_aapen_venter_uavklart_part", startet.tabell)

        // Kjøringen er fire-and-forget, så vi venter på at statistikken faktisk blir skrevet.
        ventTil { sisteAnalyze("oppgave_v3_aapen_venter_uavklart_part").let { it != null && it != før } }
    }

    /**
     * Kjernen i hvorfor endepunktet finnes: uten ANALYZE har `omrade_ekstern_id` ingen statistikk,
     * og planleggeren antar 0,5 % selektivitet i stedet for 100 %.
     */
    @Test
    fun `gir planleggeren riktig selektivitet for omrade_ekstern_id`() {
        leggInnOppgaverader(antall = 300)
        assertTrue(
            statistikkFor("oppgavefelt_verdi_aapen_venter_uavklart_part", "omrade_ekstern_id") == null,
            "Forventet ingen statistikk før ANALYZE"
        )

        val resultat = tjeneste().startAnalyze("oppgavefelt_verdi_part")

        assertInstanceOf(AnalyzeStartResultat.Startet::class.java, resultat)
        ventTil { statistikkFor("oppgavefelt_verdi_aapen_venter_uavklart_part", "omrade_ekstern_id") != null }

        val statistikk = statistikkFor("oppgavefelt_verdi_aapen_venter_uavklart_part", "omrade_ekstern_id")!!
        assertEquals(1.0f, statistikk.nDistinct, "Kolonnen har én distinkt verdi")
        assertEquals("{K9}", statistikk.mestVanligeVerdier)
        assertEquals(1.0f, statistikk.mestVanligeFrekvenser, "K9 skal dekke 100 % av radene, ikke 0,5 %")
    }

    private fun leggInnOppgaverader(antall: Int) {
        dataSource.connection.use { connection ->
            connection.createStatement().use { statement ->
                statement.execute(
                    """
                    INSERT INTO oppgave_id_part (oppgave_ekstern_id, oppgavetype_ekstern_id)
                    SELECT 'oppgave-' || i, 'k9sak' FROM generate_series(1, $antall) i
                    """.trimIndent()
                )
                statement.execute(
                    """
                    INSERT INTO oppgave_v3_part (id, omrade_ekstern_id, oppgave_ekstern_id, oppgave_ekstern_versjon,
                                                 oppgavetype_ekstern_id, reservasjonsnokkel, endret_tidspunkt,
                                                 oppgavestatus, ferdigstilt_dato)
                    SELECT id, 'K9', oppgave_ekstern_id, '1', oppgavetype_ekstern_id, 'nokkel-' || id,
                           localtimestamp, 'AAPEN', NULL
                    FROM oppgave_id_part
                    """.trimIndent()
                )
                statement.execute(
                    """
                    INSERT INTO oppgavefelt_verdi_part (oppgave_id, omrade_ekstern_id, feltdefinisjon_ekstern_id,
                                                        verdi, verdi_bigint, oppgavestatus, ferdigstilt_dato)
                    SELECT id, 'K9', 'ytelsestype', 'PSB', NULL, 'AAPEN', NULL
                    FROM oppgave_id_part
                    """.trimIndent()
                )
            }
        }
    }

    private fun sisteAnalyze(tabell: String): String? =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    "SELECT last_analyze FROM pg_stat_user_tables WHERE relname = :tabell",
                    mapOf("tabell" to tabell)
                ).map { it.stringOrNull("last_analyze") }.asSingle
            )
        }

    private fun statistikkFor(tabell: String, kolonne: String): Kolonnestatistikk? =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    SELECT n_distinct, most_common_vals::text AS mcv, most_common_freqs[1] AS mcf
                    FROM pg_stats WHERE tablename = :tabell AND attname = :kolonne
                    """.trimIndent(),
                    mapOf("tabell" to tabell, "kolonne" to kolonne)
                ).map {
                    Kolonnestatistikk(
                        nDistinct = it.float("n_distinct"),
                        mestVanligeVerdier = it.stringOrNull("mcv"),
                        mestVanligeFrekvenser = it.floatOrNull("mcf"),
                    )
                }.asSingle
            )
        }

    private data class Kolonnestatistikk(
        val nDistinct: Float,
        val mestVanligeVerdier: String?,
        val mestVanligeFrekvenser: Float?,
    )

    private fun ventTil(tidsgrenseMs: Long = 10_000, betingelse: () -> Boolean) {
        val frist = System.currentTimeMillis() + tidsgrenseMs
        while (System.currentTimeMillis() < frist) {
            if (betingelse()) return
            Thread.sleep(50)
        }
        throw AssertionError("Betingelsen ble ikke oppfylt innen ${tidsgrenseMs}ms")
    }
}
