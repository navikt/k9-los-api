package no.nav.k9.los.infrastruktur.db

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotliquery.TransactionalSession
import kotliquery.queryOf
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.eventtiloppgave.k9.K9Områdesetup
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.infrastruktur.rest.CoroutineRequestContext
import no.nav.k9.los.infrastruktur.rest.idToken
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import kotlin.test.assertEquals
import kotlin.test.assertIs

class TransactionalManagerTest : AbstractK9LosIntegrationTest() {

    private lateinit var transactionalManager: TransactionalManager

    @BeforeEach
    fun setup() {
        get<K9Områdesetup>().setup()
        transactionalManager = get()
        transactionalManager.transaction { tx ->
            tx.run(queryOf("create table if not exists transactional_manager_test (verdi text not null)").asExecute)
        }
    }

    @AfterEach
    fun ryddOpp() {
        transactionalManager.transaction { tx ->
            tx.run(queryOf("drop table if exists transactional_manager_test").asExecute)
        }
    }

    @Test
    fun `skal beholde request context i transactionSuspend`() = runTest {
        val idToken = mockk<IIdToken>()
        every { idToken.getUsername() } returns "foobar"

        val username = withContext(CoroutineRequestContext(idToken, Områder.K9)) {
            transactionalManager.transactionSuspend {
                coroutineContext.idToken().getUsername()
            }
        }

        assertEquals("foobar", username)
    }

    @Test
    fun `skal committe når operasjonen fullfører`() = runTest {
        transactionalManager.transactionSuspend { tx -> settInn(tx, "a") }

        assertEquals(listOf("a"), hentVerdier())
    }

    @Test
    fun `skal rulle tilbake når operasjonen kaster exception`() = runTest {
        val feil = runCatching {
            transactionalManager.transactionSuspend { tx ->
                settInn(tx, "a")
                throw IllegalStateException("feil")
            }
        }.exceptionOrNull()

        assertIs<IllegalStateException>(feil)
        assertEquals(emptyList(), hentVerdier())
    }

    @Test
    fun `skal rulle tilbake når coroutinen avbrytes`() = runTest {
        val skrevet = CompletableDeferred<Unit>()
        val jobb = launch(Dispatchers.Default) {
            transactionalManager.transactionSuspend { tx ->
                settInn(tx, "a")
                skrevet.complete(Unit)
                awaitCancellation()
            }
        }

        skrevet.await()
        jobb.cancelAndJoin()

        assertEquals(emptyList(), hentVerdier())
    }

    @Test
    fun `skal støtte suspensjon inne i transaksjonen`() = runTest {
        val resultat = transactionalManager.transactionSuspend { tx ->
            settInn(tx, "a")
            delay(10)
            settInn(tx, "b")
            "ferdig"
        }

        assertEquals("ferdig", resultat)
        assertEquals(listOf("a", "b"), hentVerdier())
    }

    private fun settInn(tx: TransactionalSession, verdi: String) {
        tx.run(queryOf("insert into transactional_manager_test (verdi) values (?)", verdi).asUpdate)
    }

    private fun hentVerdier(): List<String> =
        transactionalManager.transaction { tx ->
            tx.run(queryOf("select verdi from transactional_manager_test order by verdi").map { it.string("verdi") }.asList)
        }
}
