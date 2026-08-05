package no.nav.k9.los.saksbehandleradmin

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.OppgaveTestDataBuilder
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.reservasjon.ReservasjonV3Tjeneste
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder

class SaksbehandlerRepositoryTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `addSaksbehandler upserter uten a nullstille eksisterende felter`() {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        val epost = "z999999@nav.no"

        runBlocking {
            // Saksbehandler får område via admin, og feltene vedlikeholdes ved innlogging
            saksbehandlerRepository.addSaksbehandler(epost, Områder.K9)
            saksbehandlerRepository.vedlikeholdSaksbehandler(
                Saksbehandler(
                    id = null,
                    navident = "Z999999",
                    navn = "Zed Saksbehandler",
                    epost = epost,
                    enhet = "9999",
                    områder = listOf(Områder.K9)
                )
            )

            // Simulerer admin-legg-til på eksisterende epost med kun område.
            saksbehandlerRepository.addSaksbehandler(epost, Områder.K9)
        }

        val lagret = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedEpost(epost)
        }!!

        assertThat(lagret.navident, equalTo("Z999999"))
        assertThat(lagret.navn, equalTo("Zed Saksbehandler"))
        assertThat(lagret.enhet, equalTo("9999"))
        assertThat(lagret.områder, equalTo(listOf(Områder.K9)))
    }

    @Test
    fun `slette saksbehandler`() {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        val ident = "Z123456"
        val ident2 = "Z234567"

        runBlocking {
            saksbehandlerRepository.addSaksbehandler(ident + "@nav.no", Områder.K9)
            saksbehandlerRepository.vedlikeholdSaksbehandler(
                Saksbehandler(
                    null,
                    ident,
                    ident,
                    ident + "@nav.no",
                    enhet = "1234",
                    områder = listOf(Områder.K9)
                )
            )
        }

        runBlocking {
            saksbehandlerRepository.addSaksbehandler(ident2 + "@nav.no", Områder.K9)
            saksbehandlerRepository.vedlikeholdSaksbehandler(
                Saksbehandler(
                    null,
                    ident2,
                    ident2,
                    ident2 + "@nav.no",
                    enhet = "1234",
                    områder = listOf(Områder.K9)
                )
            )
        }

        val saksbehandler = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedIdent(ident)
        }!!

        val saksbehandler2 = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedIdent(ident)
        }!!

        assertThat(saksbehandler.navident, equalTo(ident))

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