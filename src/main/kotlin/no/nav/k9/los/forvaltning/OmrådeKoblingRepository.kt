package no.nav.k9.los.forvaltning

import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * Fase 2 av områdemigreringen (V1.0_0107).
 *
 * Migreringen selv gjør kun metadataoperasjoner, slik at deploy ikke kan gå på timeout uansett
 * hvor mange rader tabellene har. De to operasjonene som krever å lese gjennom hele tabellen er
 * flyttet hit, og utløses manuelt:
 *
 *  1. [validerAlleFremmednøkler] - fremmednøklene mot OMRADE legges til som NOT VALID i
 *     migreringen. De håndheves for alle nye og endrede rader fra migreringstidspunktet, men
 *     eksisterende rader er ikke verifisert før de valideres her. VALIDATE CONSTRAINT tar
 *     SHARE UPDATE EXCLUSIVE og blokkerer verken lesing eller skriving.
 *
 *  2. [opprettIndekser] - indeks på omrade_id for tabellene som er store nok til at et seq scan
 *     koster. Bygges med CREATE INDEX CONCURRENTLY, som heller ikke blokkerer skriving.
 *
 * Begge operasjonene er idempotente og kan kjøres flere ganger.
 */
class OmrådeKoblingRepository(
    private val dataSource: DataSource
) {
    private val log = LoggerFactory.getLogger(OmrådeKoblingRepository::class.java)

    companion object {
        /**
         * Tabellene som ble koblet til OMRADE i V1.0_0107. Brukes som tillatelsesliste, slik at
         * identifikatorene som interpoleres inn i DDL aldri kommer fra brukerinput.
         */
        val TABELLER_KOBLET_TIL_OMRÅDE = listOf(
            "reservasjon_v3",
            "oppgaveko_v3",
            "saksbehandler",
            "oppgave_pep_cache",
            "event_nokkel",
        )

        /**
         * Tabeller som får indeks på omrade_id, med indeksnavn.
         *
         * SAKSBEHANDLER og OPPGAVEKO_V3 er utelatt med vilje: de har i størrelsesorden hundrevis
         * av rader, og planleggeren vil velge seq scan uansett hvilket område det filtreres på.
         *
         * For de tre som er med blir fordelingen skjev - K9 dominerer og UNG blir en klart mindre
         * andel (FELLES eier feltdefinisjoner, ikke oppgaver/reservasjoner, og får normalt ingen
         * rader her). Indeksen vil derfor brukes for UNG og ignoreres for K9, som er ønsket.
         *
         * Vurdert og forkastet:
         *  - Partiell indeks (where omrade_id <> K9) ville vært langt mindre, men spørringene
         *    binder området som parameter. En generisk plan kan ikke bevise at predikatet holder,
         *    så indeksen ville blitt ignorert i praksis.
         *  - Sammensatte indekser med omrade_id først vil slå disse for spørringer som filtrerer
         *    på område *og* noe mer, men riktig kolonnesett kan ikke fastslås før de faktiske
         *    UNG-spørringene finnes. Enkle indekser er utgangspunktet.
         *
         * MERK: Disse er påkrevd før UNG tas i bruk, ikke en opprydding. Uten dem vil enhver
         * områdefiltrert spørring mot disse tabellene lese gjennom hele tabellen.
         */
        val INDEKSER: Map<String, String> = linkedMapOf(
            "reservasjon_v3" to "idx_reservasjon_v3_omrade_id",
            "event_nokkel" to "idx_event_nokkel_omrade_id",
            "oppgave_pep_cache" to "idx_oppgave_pep_cache_omrade_id",
        )
    }

    fun hentStatus(): OmrådeKoblingStatus =
        OmrådeKoblingStatus(
            fremmednøkler = hentFremmednøkkelStatus(),
            indekser = hentIndeksStatus(),
        )

    fun hentFremmednøkkelStatus(): List<FremmednøkkelStatus> {
        return using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    select rel.relname      as tabell,
                           con.conname      as constraint_navn,
                           con.convalidated as validert
                    from pg_constraint con
                             join pg_class rel on rel.oid = con.conrelid
                             join pg_namespace ns on ns.oid = rel.relnamespace
                             join pg_class ref on ref.oid = con.confrelid
                    where con.contype = 'f'
                      and ns.nspname = 'public'
                      and ref.relname = 'omrade'
                    order by rel.relname
                    """.trimIndent()
                ).map { row ->
                    FremmednøkkelStatus(
                        tabell = row.string("tabell"),
                        constraintNavn = row.string("constraint_navn"),
                        validert = row.boolean("validert"),
                    )
                }.asList
            )
        }
    }

    fun hentIndeksStatus(): List<IndeksStatus> {
        // Indeksnavnene er konstanter fra INDEKSER, aldri brukerinput, så det er trygt å bygge
        // IN-lista av dem direkte.
        val navnListe = INDEKSER.values.joinToString(", ") { "'$it'" }
        val eksisterende = using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    select idx.relname  as indeks,
                           tbl.relname  as tabell,
                           i.indisvalid as gyldig
                    from pg_index i
                             join pg_class idx on idx.oid = i.indexrelid
                             join pg_class tbl on tbl.oid = i.indrelid
                             join pg_namespace ns on ns.oid = idx.relnamespace
                    where ns.nspname = 'public'
                      and idx.relname in ($navnListe)
                    """.trimIndent()
                ).map { row ->
                    row.string("indeks") to row.boolean("gyldig")
                }.asList
            )
        }.toMap()

        return INDEKSER.map { (tabell, indeks) ->
            IndeksStatus(
                tabell = tabell,
                indeks = indeks,
                finnes = eksisterende.containsKey(indeks),
                // CREATE INDEX CONCURRENTLY kan feile halvveis og etterlate en ubrukelig indeks
                gyldig = eksisterende[indeks] ?: false,
            )
        }
    }

    /**
     * Validerer fremmednøklene som ennå ikke er validert. Hver validering kjøres som en egen
     * setning, slik at en tabell som tar lang tid ikke holder låser for de øvrige.
     */
    fun validerAlleFremmednøkler(): List<ValideringsResultat> {
        return hentFremmednøkkelStatus()
            .filterNot { it.validert }
            .filter { it.tabell in TABELLER_KOBLET_TIL_OMRÅDE }
            .map { status -> validerFremmednøkkel(status) }
    }

    private fun validerFremmednøkkel(status: FremmednøkkelStatus): ValideringsResultat {
        require(status.tabell in TABELLER_KOBLET_TIL_OMRÅDE) {
            "Ukjent tabell for områdekobling: ${status.tabell}"
        }

        val start = System.currentTimeMillis()
        return try {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf(
                        """alter table "${status.tabell}" validate constraint "${status.constraintNavn}""""
                    ).asExecute
                )
            }
            val brukteMs = System.currentTimeMillis() - start
            log.info("Validerte fremmednøkkel ${status.constraintNavn} på ${status.tabell} på $brukteMs ms")
            ValideringsResultat(status.tabell, status.constraintNavn, true, brukteMs, null)
        } catch (e: Exception) {
            val brukteMs = System.currentTimeMillis() - start
            log.error("Klarte ikke validere fremmednøkkel ${status.constraintNavn} på ${status.tabell}", e)
            ValideringsResultat(status.tabell, status.constraintNavn, false, brukteMs, e.message)
        }
    }

    /**
     * Oppretter indeksene som mangler, med CONCURRENTLY slik at skriving ikke blokkeres.
     * En indeks som finnes men er ugyldig etter en avbrutt CONCURRENTLY-bygging blir droppet
     * og bygget på nytt.
     */
    fun opprettIndekser(): List<IndeksResultat> {
        return hentIndeksStatus()
            .filterNot { it.finnes && it.gyldig }
            .map { status -> opprettIndeks(status) }
    }

    private fun opprettIndeks(status: IndeksStatus): IndeksResultat {
        require(INDEKSER[status.tabell] == status.indeks) {
            "Ukjent indeks for områdekobling: ${status.indeks} på ${status.tabell}"
        }

        val start = System.currentTimeMillis()
        return try {
            if (status.finnes && !status.gyldig) {
                log.warn("Indeks ${status.indeks} finnes men er ugyldig, dropper den før ny bygging")
                kjørUtenforTransaksjon("""drop index concurrently if exists "${status.indeks}"""")
            }

            kjørUtenforTransaksjon(
                """create index concurrently if not exists "${status.indeks}" on "${status.tabell}" (omrade_id)"""
            )

            val brukteMs = System.currentTimeMillis() - start
            log.info("Opprettet indeks ${status.indeks} på ${status.tabell} på $brukteMs ms")
            IndeksResultat(status.tabell, status.indeks, true, brukteMs, null)
        } catch (e: Exception) {
            val brukteMs = System.currentTimeMillis() - start
            log.error("Klarte ikke opprette indeks ${status.indeks} på ${status.tabell}", e)
            IndeksResultat(status.tabell, status.indeks, false, brukteMs, e.message)
        }
    }

    /**
     * CREATE/DROP INDEX CONCURRENTLY kan ikke kjøre inne i en transaksjon, så vi bruker en
     * connection med autocommit framfor kotliquery sin transaksjonshåndtering.
     */
    private fun kjørUtenforTransaksjon(sql: String) {
        dataSource.connection.use { connection ->
            val opprinneligAutoCommit = connection.autoCommit
            try {
                connection.autoCommit = true
                connection.createStatement().use { it.execute(sql) }
            } finally {
                connection.autoCommit = opprinneligAutoCommit
            }
        }
    }
}

data class OmrådeKoblingStatus(
    val fremmednøkler: List<FremmednøkkelStatus>,
    val indekser: List<IndeksStatus>,
)

data class FremmednøkkelStatus(
    val tabell: String,
    val constraintNavn: String,
    val validert: Boolean,
)

data class IndeksStatus(
    val tabell: String,
    val indeks: String,
    val finnes: Boolean,
    val gyldig: Boolean,
)

data class ValideringsResultat(
    val tabell: String,
    val constraintNavn: String,
    val validert: Boolean,
    val brukteMs: Long,
    val feilmelding: String?,
)

data class IndeksResultat(
    val tabell: String,
    val indeks: String,
    val opprettet: Boolean,
    val brukteMs: Long,
    val feilmelding: String?,
)



