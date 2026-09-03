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
    fun `leggTilOmråde endrer ikke eksisterende felter`() {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        val epost = "z999999@nav.no"
        val oppdatertTidspunkt = LocalDateTime.parse("2026-08-28T10:00:00")

        runBlocking {
            // Saksbehandler får område via admin, og feltene vedlikeholdes ved innlogging
            val id = saksbehandlerRepository.opprettSaksbehandler(epost, Områder.K9)
            saksbehandlerRepository.vedlikeholdSaksbehandler(
                Saksbehandler(
                    id = id,
                    navident = "Z999999",
                    navn = "Zed Saksbehandler",
                    epost = epost,
                    enhet = "9999",
                    områder = listOf(Områder.K9),
                    kode6 = false
                ),
                skjermet = false,
                oppdatertTidspunkt = oppdatertTidspunkt,
            )

            saksbehandlerRepository.leggTilOmråde(id, Områder.K9)
        }

        val lagret = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedEpost(epost, skjermet = false)
        }!!

        assertThat(lagret.navident, equalTo("Z999999"))
        assertThat(lagret.navn, equalTo("Zed Saksbehandler"))
        assertThat(lagret.enhet, equalTo("9999"))
        assertThat(lagret.områder, equalTo(listOf(Områder.K9)))
        assertThat(lagret.sistOppdatert, equalTo(oppdatertTidspunkt))
    }

    @Test
    fun `vedlikeholder saksbehandler og tidspunkt`() = runBlocking {
        val repository = get<SaksbehandlerRepository>()
        val id = repository.opprettSaksbehandler("saksbehandler@nav.no", Områder.K9)
        val tidspunkt = LocalDateTime.parse("2026-08-28T10:00:00")

        repository.vedlikeholdSaksbehandler(
            Saksbehandler(id, "Z654321", "Nytt navn", "ny.epost@nav.no", "3450", listOf(Områder.K9), false),
            skjermet = false,
            oppdatertTidspunkt = tidspunkt,
        )

        val oppdatert = repository.finnSaksbehandlerMedId(id)!!
        assertThat(oppdatert.navident, equalTo("Z654321"))
        assertThat(oppdatert.navn, equalTo("Nytt navn"))
        assertThat(oppdatert.epost, equalTo("ny.epost@nav.no"))
        assertThat(oppdatert.enhet, equalTo("3450"))
        assertThat(oppdatert.sistOppdatert, equalTo(tidspunkt))
    }

    @Test
    fun `slette saksbehandler`() {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        val testSaksbehandlerRepository = get<TestSaksbehandlerRepository>()
        val ident = "Z123456"
        val ident2 = "Z234567"

        testSaksbehandlerRepository.opprettSaksbehandler(
            OpprettSaksbehandler(ident, ident, ident + "@nav.no", "1234"),
            Områder.K9,
            skjermet = false,
        )

        testSaksbehandlerRepository.opprettSaksbehandler(
            OpprettSaksbehandler(ident2, ident2, ident2 + "@nav.no", "1234"),
            Områder.K9,
            skjermet = false,
        )

        val saksbehandler = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedIdent(ident, skjermet = false)
        }!!

        val saksbehandler2 = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedIdent(ident, skjermet = false)
        }!!

        assertThat(saksbehandler.navident, equalTo(ident))

        val builder = OppgaveTestDataBuilder()
        builder.lagOgLagre(Oppgavestatus.AAPEN)
        builder.lagre(builder.lag(reservasjonsnøkkel = "test"))

        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()

        runBlocking {
            reservasjonV3Tjeneste.taReservasjon("test", saksbehandler.id, saksbehandler.id, "test", LocalDateTime.now(), LocalDateTime.now().plusDays(1))
        }

        reservasjonV3Tjeneste.forlengReservasjon("test", LocalDateTime.now().plusDays(2), saksbehandler.id, "test")

        reservasjonV3Tjeneste.overførReservasjon("test", LocalDateTime.now().plusDays(1), saksbehandler2.id, saksbehandler2.id, "kommentar")

        val transactionalManager = get<TransactionalManager>()
        transactionalManager.transaction { tx ->
            saksbehandlerRepository.slettSaksbehandler(tx, ident+"@nav.no", false)
        }
    }
}
