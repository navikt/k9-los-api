package no.nav.k9.los.nyoppgavestyring.infrastruktur.db

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.OmrådeSetup
import no.nav.k9.los.nyoppgavestyring.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.CoroutineRequestContext
import no.nav.k9.los.nyoppgavestyring.infrastruktur.rest.idToken
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import kotlin.coroutines.coroutineContext
import kotlin.test.assertEquals

class TransactionalManagerTest : AbstractK9LosIntegrationTest() {

    private lateinit var transactionalManager: TransactionalManager

    @BeforeEach
    fun setup() {
        get<OmrådeSetup>().setup()
        transactionalManager = get()
    }

    @Test
    fun `skal beholde request context i transactionSuspend`() {
        val idToken = mockk<IIdToken>()
        every { idToken.getUsername() } returns "foobar"

        val username = runBlocking {
            withContext(CoroutineRequestContext(idToken)) {
                transactionalManager.transactionSuspend {
                    coroutineContext.idToken().getUsername()
                }
            }
        }

        assertEquals("foobar", username)
    }
}
