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
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import org.postgresql.util.PSQLException
import java.time.LocalDateTime

class SaksbehandlerRepositoryTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `vedlikeholder saksbehandler og tidspunkt`() = runBlocking {
        val testSaksbehandlerRepository = get<TestSaksbehandlerRepository>()
        val repository = get<SaksbehandlerRepository>()
        val opprinnelig = OpprettSaksbehandler( "Z123456", "Gammelt navn", "saksbehandler@nav.no", "1234")
        val id = testSaksbehandlerRepository.opprettSaksbehandler(opprinnelig).id
        val tidspunkt = LocalDateTime.parse("2026-08-28T10:00:00")

        repository.vedlikeholdSaksbehandler(
            Saksbehandler(id, "Z654321", "Nytt navn", "Ny.Epost@nav.no", "3450"),
            tidspunkt
        )

        val oppdatert = repository.finnSaksbehandlerMedId(id)!!
        assertThat(oppdatert.id, equalTo(id))
        assertThat(oppdatert.epost, equalTo("ny.epost@nav.no"))
        assertThat(oppdatert.navident, equalTo("Z654321"))
        assertThat(oppdatert.navn, equalTo("Nytt navn"))
        assertThat(oppdatert.enhet, equalTo("3450"))
        assertThat(oppdatert.sistOppdatert, equalTo(tidspunkt))
    }

    @Test
    fun `id er primaernokkel og epost er obligatorisk og unik`() {
        dataSource.connection.use { connection ->
            connection.metaData.getPrimaryKeys(null, "public", "saksbehandler").use { keys ->
                val kolonner = buildList {
                    while (keys.next()) add(keys.getString("COLUMN_NAME"))
                }
                assertThat(kolonner, equalTo(listOf("id")))
            }
            connection.createStatement().use { statement ->
                val manglendeEpost = assertThrows<PSQLException> {
                    statement.executeUpdate("insert into saksbehandler (epost) values (null)")
                }
                assertThat(manglendeEpost.sqlState, equalTo("23502"))

                statement.executeUpdate("insert into saksbehandler (epost) values ('unik@nav.no')")
                val duplikat = assertThrows<PSQLException> {
                    statement.executeUpdate("insert into saksbehandler (epost) values ('unik@nav.no')")
                }
                assertThat(duplikat.sqlState, equalTo("23505"))
                assertThat(duplikat.serverErrorMessage?.constraint, equalTo("saksbehandler_epost_key"))
            }
        }
    }

    @Test
    fun `avviser epost som tilhorer en annen saksbehandler uten delvis oppdatering`() = runBlocking {
        val testRepository = get<TestSaksbehandlerRepository>()
        val repository = get<SaksbehandlerRepository>()
        val opprinnelig = testRepository.opprettSaksbehandler(
            OpprettSaksbehandler("Z123456", "Gammelt navn", "gammel@nav.no", "1234")
        )
        val annen = testRepository.opprettSaksbehandler(
            OpprettSaksbehandler("Z234567", "Annen saksbehandler", "opptatt@nav.no", "2345")
        )

        val feil = assertThrows<PSQLException> {
            repository.vedlikeholdSaksbehandler(
                Saksbehandler(opprinnelig.id, "Z654321", "Nytt navn", annen.epost, "3450"),
                LocalDateTime.parse("2026-08-28T10:00:00")
            )
        }

        assertThat(feil.sqlState, equalTo("23505"))
        val uendret = repository.finnSaksbehandlerMedId(opprinnelig.id)!!
        assertThat(uendret.epost, equalTo(opprinnelig.epost))
        assertThat(uendret.navident, equalTo(opprinnelig.navident))
        assertThat(uendret.navn, equalTo(opprinnelig.navn))
        assertThat(uendret.enhet, equalTo(opprinnelig.enhet))
        assertThat(uendret.sistOppdatert, equalTo(opprinnelig.sistOppdatert))
        assertThat(repository.finnSaksbehandlerMedId(annen.id)!!.epost, equalTo(annen.epost))
    }

    @Test
    fun `slette saksbehandler`() {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        val testSaksbehandlerRepository = get<TestSaksbehandlerRepository>()
        val ident = "Z123456"
        val ident2 = "Z234567"

        runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    ident,
                    ident,
                    ident + "@nav.no",
                    enhet = "1234"
                )
            )
        }

        runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
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

        assertThat(saksbehandler.navident, equalTo(ident))

        val builder = OppgaveTestDataBuilder()
        builder.lagOgLagre(Oppgavestatus.AAPEN)
        builder.lagre(builder.lag(reservasjonsnøkkel = "test"))

        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()

        reservasjonV3Tjeneste.taReservasjon("test", saksbehandler.id, saksbehandler.id, "test", LocalDateTime.now(), LocalDateTime.now().plusDays(1))

        reservasjonV3Tjeneste.forlengReservasjon("test", LocalDateTime.now().plusDays(2), saksbehandler.id, "test")

        reservasjonV3Tjeneste.overførReservasjon("test", LocalDateTime.now().plusDays(1), saksbehandler2.id, saksbehandler2.id, "kommentar")

        val transactionalManager = get<TransactionalManager>()
        transactionalManager.transaction { tx ->
            saksbehandlerRepository.slettSaksbehandler(tx, ident+"@nav.no", false)
        }
    }
}
