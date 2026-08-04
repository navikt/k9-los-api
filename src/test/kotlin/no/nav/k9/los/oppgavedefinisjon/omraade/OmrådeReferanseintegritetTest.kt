package no.nav.k9.los.oppgavedefinisjon.omraade

import assertk.assertThat
import assertk.assertions.containsAll
import assertk.assertions.isEmpty
import kotlinx.coroutines.runBlocking
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.forvaltning.OmrådeKoblingRepository
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import org.postgresql.util.PSQLException

/**
 * Området er koblingspunktet som sikrer at rader i tilstøtende tabeller alltid peker på et
 * eksisterende område, og at et område som er i bruk ikke kan slettes.
 *
 * Testene her verner om selve koblingen, slik at den ikke kan svekkes uoppdaget i en senere
 * migrering.
 */
class OmrådeReferanseintegritetTest : AbstractK9LosIntegrationTest() {

    private val tabellerKobletTilOmråde = listOf(
        "reservasjon_v3",
        "oppgaveko_v3",
        "saksbehandler_omrade",
        "oppgave_pep_cache",
        "event_nokkel",
    )

    @Test
    fun `omrade_id skal ha fremmednokkel mot omrade i alle koblede tabeller`() {
        val tabellerMedFremmednøkkel = using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    select tc.table_name
                    from information_schema.table_constraints tc
                    join information_schema.key_column_usage kcu
                      on tc.constraint_name = kcu.constraint_name
                     and tc.table_schema = kcu.table_schema
                    join information_schema.constraint_column_usage ccu
                      on tc.constraint_name = ccu.constraint_name
                     and tc.table_schema = ccu.table_schema
                    where tc.constraint_type = 'FOREIGN KEY'
                      and tc.table_schema = 'public'
                      and kcu.column_name = 'omrade_id'
                      and ccu.table_name = 'omrade'
                    """.trimIndent()
                ).map { it.string("table_name") }.asList
            )
        }

        assertThat(tabellerMedFremmednøkkel).containsAll(*tabellerKobletTilOmråde.toTypedArray())
    }

    @Test
    fun `omrade_id skal vare not null i alle koblede tabeller`() {
        val nullbareKolonner = using(sessionOf(dataSource)) { session ->
            session.run(
                queryOf(
                    """
                    select table_name
                    from information_schema.columns
                    where table_schema = 'public'
                      and column_name = 'omrade_id'
                      and is_nullable = 'YES'
                    """.trimIndent()
                ).map { it.string("table_name") }.asList
            )
        }.filter { it in tabellerKobletTilOmråde }

        assertThat(nullbareKolonner).isEmpty()
    }

    @Test
    fun `skal ikke kunne slette et omrade som er i bruk`() {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    navident = "Z999001",
                    navn = "Test Testesen",
                    epost = "referanseintegritet@test.no",
                    enhet = null,
                    områder = listOf(Områder.K9),
                )
            )
        }

        val exception = assertThrows<PSQLException> {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf(
                        "delete from omrade where ekstern_id = :eksternId",
                        mapOf("eksternId" to Områder.K9.eksternId)
                    ).asUpdate
                )
            }
        }

        // 23503 = foreign_key_violation
        assertThat(listOf(exception.sqlState)).containsAll("23503")
    }

    @Test
    fun `skal ikke kunne referere et omrade som ikke finnes`() {
        val exception = assertThrows<PSQLException> {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf(
                        """
                        with ny_saksbehandler as (
                            insert into saksbehandler (navident, navn, epost, enhet, skjermet)
                            values ('Z999002', 'Ukjent Omraade', 'ukjent-omrade@test.no', null, false)
                            returning id
                        )
                        insert into saksbehandler_omrade (saksbehandler_id, omrade_id)
                        select id, 999999
                        from ny_saksbehandler
                        """.trimIndent()
                    ).asUpdate
                )
            }
        }

        assertThat(listOf(exception.sqlState)).containsAll("23503")
    }

    /**
     * De store tabellene får fremmednøkkelen som NOT VALID i migreringen, for å unngå table scan
     * ved deploy. NOT VALID betyr kun at eksisterende rader ikke er verifisert - nye rader skal
     * fortsatt avvises. Denne testen verner om nettopp det.
     */
    @Test
    fun `not valid fremmednokkel skal likevel avvise nye rader med ukjent omrade`() {
        val exception = assertThrows<PSQLException> {
            using(sessionOf(dataSource)) { session ->
                session.run(
                    queryOf(
                        """
                        insert into event_nokkel (ekstern_id, fagsystem, omrade_id)
                        values ('ukjent-omrade-test', 'K9SAK', 999999)
                        """.trimIndent()
                    ).asUpdate
                )
            }
        }

        assertThat(listOf(exception.sqlState)).containsAll("23503")
    }

    @Test
    fun `validering av fremmednokler skal gjore alle validert og vare idempotent`() {
        val repository = OmrådeKoblingRepository(dataSource)

        repository.validerAlleFremmednøkler()

        val etterFørsteKjøring = repository.hentFremmednøkkelStatus()
        assertThat(etterFørsteKjøring.filterNot { it.validert }).isEmpty()
        assertThat(etterFørsteKjøring.map { it.tabell })
            .containsAll(*tabellerKobletTilOmråde.toTypedArray())

        // Ny kjøring skal ikke gjøre noe, siden alt allerede er validert
        val andreKjøring = repository.validerAlleFremmednøkler()
        assertThat(andreKjøring).isEmpty()
    }

    @Test
    fun `oppretting av indekser skal gi gyldige indekser og vare idempotent`() {
        val repository = OmrådeKoblingRepository(dataSource)

        val førsteKjøring = repository.opprettIndekser()
        assertThat(førsteKjøring.filterNot { it.opprettet }).isEmpty()

        val status = repository.hentIndeksStatus()
        assertThat(status.map { it.tabell })
            .containsAll(*OmrådeKoblingRepository.INDEKSER.keys.toTypedArray())
        assertThat(status.filterNot { it.finnes && it.gyldig }).isEmpty()

        // Ny kjøring skal ikke bygge noe på nytt
        val andreKjøring = repository.opprettIndekser()
        assertThat(andreKjøring).isEmpty()
    }
}





