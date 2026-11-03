package no.nav.k9.los.forvaltning

import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.Configuration
import no.nav.k9.los.KoinProfile
import no.nav.k9.los.infrastruktur.db.Role
import no.nav.k9.los.infrastruktur.db.dataSourceFromVault
import org.slf4j.LoggerFactory
import java.sql.Connection
import java.sql.SQLWarning
import java.util.concurrent.ConcurrentHashMap
import javax.sql.DataSource
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TimeSource

/**
 * Tilkobling med rettigheter til å kjøre ANALYZE.
 *
 * ANALYZE krever eierskap til tabellen. Applikasjonen kjører som `<db>-user`, mens tabellene eies av
 * `<db>-admin` (Flyway kjører med SET ROLE, se [no.nav.k9.los.infrastruktur.db.migrate]). Kjøres
 * ANALYZE med feil rolle, feiler den ikke — PostgreSQL gir en SQLWarning og hopper over tabellen.
 */
fun interface AdminTilkobling {
    fun bruk(block: (Connection) -> Unit)

    companion object {
        fun fraConfiguration(configuration: Configuration): AdminTilkobling = AdminTilkobling { block ->
            val dataSource = if (configuration.koinProfile() == KoinProfile.LOCAL) {
                HikariDataSource(configuration.hikariConfig())
            } else {
                dataSourceFromVault(configuration, Role.Admin)
            }
            dataSource.use { ds ->
                ds.connection.use { connection ->
                    if (configuration.koinProfile() != KoinProfile.LOCAL) {
                        connection.createStatement().use {
                            it.execute("SET ROLE \"${configuration.databaseName()}-${Role.Admin}\"")
                        }
                    }
                    block(connection)
                }
            }
        }
    }
}

/**
 * Kjører ANALYZE på partisjonstabellene for oppgaver.
 *
 * Bakgrunn: `omrade_ekstern_id` ble lagt til i V1.0_0109 med `not null default 'K9'`. Kolonnen har
 * ingen statistikk før ANALYZE har kjørt, og planleggeren antar da 0,5 % selektivitet i stedet for
 * 100 %. Autoanalyze redder partisjonene det skrives til, men aldri de frosne `_lukket_20xx_part`-
 * partisjonene, og aldri den partisjonerte foreldretabellen.
 */
class AnalyzeTjeneste(
    private val dataSource: DataSource,
    private val adminTilkobling: AdminTilkobling,
) {
    private val log = LoggerFactory.getLogger(AnalyzeTjeneste::class.java)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pågående = ConcurrentHashMap.newKeySet<String>()

    /**
     * Starter ANALYZE i bakgrunnen og returnerer med én gang. Validering av tabellnavnet skjer
     * synkront, slik at kallet svarer med feil på ugyldig navn framfor at man må lete i loggen.
     */
    fun startAnalyze(tabellnavn: String): AnalyzeStartResultat {
        if (!erTillattTabell(tabellnavn)) {
            return AnalyzeStartResultat.UgyldigTabell(
                "Tabellen '$tabellnavn' er ikke i hvitelisten. Navnet må starte med en av " +
                    "${TILLATTE_PREFIKSER.joinToString(", ")} og slutte på '$PÅKREVD_SUFFIKS'."
            )
        }

        val tabell = slåOppTabell(tabellnavn)
            ?: return AnalyzeStartResultat.UgyldigTabell("Fant ingen tabell med navn '$tabellnavn' i schema public.")

        if (!pågående.add(tabell.navn)) {
            return AnalyzeStartResultat.AlleredeIGang(tabell.navn)
        }

        scope.launch {
            try {
                kjørAnalyze(tabell)
            } finally {
                pågående.remove(tabell.navn)
            }
        }

        return AnalyzeStartResultat.Startet(tabell.navn, tabell.størrelse)
    }

    private suspend fun kjørAnalyze(tabell: Tabell) {
        val startet = TimeSource.Monotonic.markNow()
        log.info("ANALYZE startet for {} (størrelse {})", tabell.navn, tabell.størrelse)

        val hjerteslag = scope.launch {
            var minutter = 0
            while (isActive) {
                delay(1.minutes)
                minutter++
                log.info("ANALYZE pågår fortsatt for {}, har kjørt i {} minutt(er)", tabell.navn, minutter)
            }
        }

        try {
            var warnings: List<String> = emptyList()
            adminTilkobling.bruk { connection ->
                connection.createStatement().use { statement ->
                    // tabell.navn kommer fra pg_class via regclass, ikke fra brukerinput.
                    statement.execute("ANALYZE ${tabell.navn}")
                    warnings = statement.warnings.tilListe()
                }
            }

            if (warnings.isEmpty()) {
                log.info("ANALYZE ferdig for {}, tidsbruk {}", tabell.navn, startet.elapsedNow())
            } else {
                // Typisk "skipping ... only table or database owner can analyze it", som betyr at
                // ANALYZE ikke har gjort noe som helst.
                log.error(
                    "ANALYZE ferdig for {} med {} warning(s), tidsbruk {}. Statistikken er sannsynligvis IKKE oppdatert. Warnings: {}",
                    tabell.navn, warnings.size, startet.elapsedNow(), warnings.joinToString(" | ")
                )
            }
        } catch (e: Exception) {
            log.error("ANALYZE feilet for {} etter {}", tabell.navn, startet.elapsedNow(), e)
        } finally {
            hjerteslag.cancel()
        }
    }

    private fun erTillattTabell(tabellnavn: String): Boolean =
        tabellnavn.endsWith(PÅKREVD_SUFFIKS) && TILLATTE_PREFIKSER.any { tabellnavn.startsWith(it) }

    /**
     * Slår opp tabellen i pg_class med bind-parameter og returnerer navnet slik katalogen skriver det.
     * ANALYZE tar ikke bind-parameter for tabellnavn, så navnet må interpoleres inn i kommandoen.
     * Ved å hente navnet tilbake via regclass er det katalogen, ikke brukerinput, som ender i SQL-en.
     */
    private fun slåOppTabell(tabellnavn: String): Tabell? =
        using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    SELECT c.oid::regclass::text AS navn,
                           pg_size_pretty(
                               (SELECT COALESCE(SUM(pg_total_relation_size(p.relid)), 0)
                                FROM pg_partition_tree(c.oid) p)
                           ) AS storrelse
                    FROM pg_class c
                    INNER JOIN pg_namespace n ON n.oid = c.relnamespace
                    WHERE n.nspname = 'public'
                      AND c.relname = :tabell
                      AND c.relkind IN ('r', 'p')
                    """.trimIndent(),
                    mapOf("tabell" to tabellnavn)
                ).map { row -> Tabell(navn = row.string("navn"), størrelse = row.string("storrelse")) }.asSingle
            )
        }

    private fun SQLWarning?.tilListe(): List<String> {
        val resultat = mutableListOf<String>()
        var warning = this
        while (warning != null && resultat.size < MAKS_ANTALL_WARNINGS) {
            resultat.add(warning.message ?: warning.toString())
            warning = warning.nextWarning
        }
        return resultat
    }

    private data class Tabell(val navn: String, val størrelse: String)

    companion object {
        private val TILLATTE_PREFIKSER = listOf("oppgave_v3", "oppgavefelt_verdi")
        private const val PÅKREVD_SUFFIKS = "_part"
        private const val MAKS_ANTALL_WARNINGS = 20
    }
}

sealed class AnalyzeStartResultat {
    data class Startet(val tabell: String, val størrelse: String) : AnalyzeStartResultat()
    data class AlleredeIGang(val tabell: String) : AnalyzeStartResultat()
    data class UgyldigTabell(val begrunnelse: String) : AnalyzeStartResultat()
}

data class AnalyzeStartetResponse(
    val tabell: String,
    val størrelse: String,
    val melding: String,
)
