package no.nav.k9.los.infrastruktur.db

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.k9.OmrådeSetup
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import kotlin.test.assertEquals

class TransactionalManagerTest : AbstractK9LosIntegrationTest() {

    private lateinit var transactionalManager: TransactionalManager

    @BeforeEach
    fun setup() {
        get<OmrådeSetup>().setup()
        transactionalManager = get()
    }

    @Test
    fun `skal kunne kjøre transaksjon i suspend-kontekst`() {
        val resultat = runBlocking {
            transactionalManager.transactionSuspend {
                "foobar"
            }
        }

        assertEquals("foobar", resultat)
    }
}