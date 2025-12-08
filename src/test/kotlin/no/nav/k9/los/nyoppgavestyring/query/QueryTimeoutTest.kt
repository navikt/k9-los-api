package no.nav.k9.los.nyoppgavestyring.query

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isInstanceOf
import kotliquery.queryOf
import kotliquery.sessionOf
import kotliquery.using
import no.nav.k9.los.AbstractPostgresTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException

class QueryTimeoutTest : AbstractPostgresTest() {

    @Test
    fun `kotliquery queryTimeout fungerer IKKE med transaksjoner - dette er en kjent bug`() {
        // Denne testen demonstrerer at kotliquery sin queryTimeout parameter
        // ikke sendes videre til TransactionalSession, og derfor ikke virker
        // for spørringer som kjøres innenfor en transaksjon.

        // Vi forventer at denne IKKE kaster timeout-exception,
        // fordi queryTimeout ikke arves til TransactionalSession
        using(sessionOf(dataSource, queryTimeout = 1)) { session ->
            session.transaction { tx ->
                // pg_sleep(2) sover i 2 sekunder - burde time out med queryTimeout=1
                // Men den vil IKKE time out fordi TransactionalSession ikke arver queryTimeout
                tx.run(queryOf("SELECT pg_sleep(2)").asExecute)
            }
        }
        // Hvis vi kommer hit uten exception, bekrefter det at queryTimeout ikke fungerer
    }

    @Test
    fun `PostgreSQL statement_timeout fungerer med transaksjoner`() {
        // Denne testen verifiserer at SET LOCAL statement_timeout fungerer
        // som et alternativ til kotliquery sin queryTimeout

        val exception = assertThrows<PSQLException> {
            using(sessionOf(dataSource)) { session ->
                session.transaction { tx ->
                    // Sett timeout til 1000ms (1 sekund) for denne transaksjonen
                    tx.run(queryOf("SET LOCAL statement_timeout = 1000").asExecute)
                    // pg_sleep(2) sover i 2 sekunder - burde time out
                    tx.run(queryOf("SELECT pg_sleep(2)").asExecute)
                }
            }
        }

        assertThat(exception).isInstanceOf(PSQLException::class)
        // PSQLException med melding som inneholder "canceling statement due to statement timeout"
    }

    @Test
    fun `statement_timeout i millisekunder fungerer korrekt`() {
        // Verifiser at timeout i millisekunder fungerer
        val exception = assertThrows<PSQLException> {
            using(sessionOf(dataSource)) { session ->
                session.transaction { tx ->
                    // Sett timeout til 500ms
                    tx.run(queryOf("SET LOCAL statement_timeout = 500").asExecute)
                    // pg_sleep(1) sover i 1 sekund - burde time out etter 500ms
                    tx.run(queryOf("SELECT pg_sleep(1)").asExecute)
                }
            }
        }

        assertThat(exception).isInstanceOf(PSQLException::class)
    }

    @Test
    fun `spørring som fullfører før timeout skal ikke feile`() {
        // Verifiser at spørringer som fullfører før timeout ikke feiler
        using(sessionOf(dataSource)) { session ->
            session.transaction { tx ->
                // Sett timeout til 5000ms (5 sekunder)
                tx.run(queryOf("SET LOCAL statement_timeout = 5000").asExecute)
                // pg_sleep(0.1) sover i 100ms - skal fullføre før timeout
                tx.run(queryOf("SELECT pg_sleep(0.1)").asExecute)
            }
        }
        // Hvis vi kommer hit uten exception, fungerer det som forventet
    }

    @Test
    fun `verifiserer at PSQLException inneholder riktig feilmelding ved timeout`() {
        val exception = assertThrows<PSQLException> {
            using(sessionOf(dataSource)) { session ->
                session.transaction { tx ->
                    tx.run(queryOf("SET LOCAL statement_timeout = 100").asExecute)
                    tx.run(queryOf("SELECT pg_sleep(1)").asExecute)
                }
            }
        }

        // Verifiser at feilmeldingen inneholder informasjon om timeout
        assertThat(exception.message.orEmpty()).contains("statement timeout")
    }
}
