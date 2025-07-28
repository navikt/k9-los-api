package no.nav.k9.los.nyoppgavestyring.saksbehandleradmin

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.koin.test.get

class SaksbehandlerRepositoryTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `slette saksbehandler`() {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        val ident = "Z123456"

        runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    null,
                    ident,
                    ident,
                    ident + "@nav.no",
                    enhet = "1234"
                )
            )
        }

        val saksbehandler = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedIdent(ident)
        }

        assertThat(saksbehandler!!.brukerIdent, equalTo(ident))

        val transactionalManager = get<TransactionalManager>()
        transactionalManager.transaction { tx ->
            saksbehandlerRepository.slettSaksbehandler(tx, ident+"@nav.no", false)
        }
    }
}