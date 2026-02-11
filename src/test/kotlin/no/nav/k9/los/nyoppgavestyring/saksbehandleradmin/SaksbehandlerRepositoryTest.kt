package no.nav.k9.los.nyoppgavestyring.saksbehandleradmin

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.reservasjon.ReservasjonV3Tjeneste
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime

class SaksbehandlerRepositoryTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `slette saksbehandler`() {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        val ident = "Z123456"
        val ident2 = "Z234567"

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

        runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    null,
                    ident2,
                    ident2,
                    ident2 + "@nav.no",
                    enhet = "1234"
                )
            )
        }

        val saksbehandler = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedIdent(ident)
        }!!

        val saksbehandler2 = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedIdent(ident)
        }!!

        assertThat(saksbehandler.brukerIdent, equalTo(ident))

        val builder = OppgaveTestDataBuilder()
        builder.lagOgLagre(Oppgavestatus.AAPEN)
        builder.lagre(builder.lag(reservasjonsnøkkel = "test"))

        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()

        val reservasjon = reservasjonV3Tjeneste.taReservasjon("test", saksbehandler.id!!, saksbehandler.id!!, "test", LocalDateTime.now(), LocalDateTime.now().plusDays(1))

        reservasjonV3Tjeneste.forlengReservasjon("test", LocalDateTime.now().plusDays(2), saksbehandler.id!!, "test")

        reservasjonV3Tjeneste.overførReservasjon("test", LocalDateTime.now().plusDays(1), saksbehandler2.id!!, saksbehandler2.id!!, "kommentar")

        val transactionalManager = get<TransactionalManager>()
        transactionalManager.transaction { tx ->
            saksbehandlerRepository.slettSaksbehandler(tx, ident+"@nav.no", false)
        }
    }
}