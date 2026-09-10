package no.nav.k9.los.saksbehandleradmin

import kotlinx.coroutines.runBlocking
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import no.nav.k9.los.infrastruktur.azuregraph.IAzureGraphService
import no.nav.k9.los.infrastruktur.brukerkontekst.TestKontekstFactory
import no.nav.k9.los.infrastruktur.idtoken.IIdToken
import no.nav.k9.los.innloggetbruker.InnloggetBrukerTjeneste
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.OppgaveTestDataBuilder
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.reservasjon.ReservasjonV3Tjeneste
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.postgresql.util.PSQLException
import org.koin.test.get
import java.time.LocalDateTime
import java.time.Clock
import java.time.ZoneOffset
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository

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
                    skjermet = false,
                    sistOppdatert = oppdatertTidspunkt,
                ),
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
            Saksbehandler(id, "Z654321", "Nytt navn", "Ny.Epost@nav.no", "3450", listOf(Områder.K9), false, tidspunkt)
        )

        val oppdatert = repository.finnSaksbehandlerMedId(id)!!
        assertThat(oppdatert.navident, equalTo("Z654321"))
        assertThat(oppdatert.navn, equalTo("Nytt navn"))
        assertThat(oppdatert.epost, equalTo("ny.epost@nav.no"))
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
                Saksbehandler(opprinnelig.id, "Z654321", "Nytt navn", annen.epost, "3450", opprinnelig.områder, false, LocalDateTime.parse("2026-08-28T10:00:00"))
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
    fun `vedlikehold fortsetter etter opprydding av epostkonflikt`() = runBlocking {
        val repository = get<SaksbehandlerRepository>()
        val opprinnelig = get<TestSaksbehandlerRepository>().opprettSaksbehandler(
            OpprettSaksbehandler("Z123456", "Gammelt navn", "x@nav.no", "1234")
        )
        val duplikatId = repository.opprettSaksbehandler("y@nav.no", Områder.K9)
        val duplikat = repository.finnSaksbehandlerMedId(duplikatId)!!
        val tidspunkt = opprinnelig.sistOppdatert!!.plusDays(2)
        val token = mockk<IIdToken> {
            every { getNavIdent() } returns "Z123456"
            every { getName() } returns "Nytt navn"
            every { getPreferredUsername() } returns "y@nav.no"
        }
        val bruker = TestKontekstFactory.brukerkontekst(Områder.K9, idToken = token)
        val graph = mockk<IAzureGraphService>()
        coEvery { graph.hentEnhet(bruker.navIdent, token) } returns "3450"
        val tjeneste = InnloggetBrukerTjeneste(
            repository, graph, Clock.fixed(tidspunkt.toInstant(ZoneOffset.UTC), ZoneOffset.UTC)
        )

        tjeneste.hentInnloggetBruker(bruker)

        listOf(opprinnelig, duplikat).forEach { før ->
            val etter = repository.finnSaksbehandlerMedId(før.id)!!
            assertThat(etter.id, equalTo(før.id))
            assertThat(etter.epost, equalTo(før.epost))
            assertThat(etter.navident, equalTo(før.navident))
            assertThat(etter.navn, equalTo(før.navn))
            assertThat(etter.enhet, equalTo(før.enhet))
            assertThat(etter.sistOppdatert, equalTo(før.sistOppdatert))
        }

        dataSource.connection.use { connection ->
            connection.prepareStatement("delete from saksbehandler where id = ?").use { statement ->
                statement.setLong(1, duplikatId)
                assertThat(statement.executeUpdate(), equalTo(1))
            }
        }
        tjeneste.hentInnloggetBruker(bruker)

        val oppdatert = repository.finnSaksbehandlerMedId(opprinnelig.id)!!
        assertThat(oppdatert.id, equalTo(opprinnelig.id))
        assertThat(oppdatert.epost, equalTo("y@nav.no"))
        assertThat(oppdatert.navident, equalTo("Z123456"))
        assertThat(oppdatert.navn, equalTo("Nytt navn"))
        assertThat(oppdatert.enhet, equalTo("3450"))
        assertThat(oppdatert.sistOppdatert, equalTo(tidspunkt))
    }

    @Test
    fun `vedlikehold oppdaterer global skjerming uten aa endre andre omraadekoblinger`() {
        val repository = get<SaksbehandlerRepository>()
        get<OmrådeRepository>().lagre(Områder.AKTIVITETSPENGER.eksternId)
        val id = repository.opprettSaksbehandler("flere@nav.no", Områder.K9)
        repository.leggTilOmråde(id, Områder.AKTIVITETSPENGER)
        val tidspunkt = LocalDateTime.parse("2026-08-28T10:00:00")

        listOf(true, false).forEach { kode6 ->
            repository.vedlikeholdSaksbehandler(
                Saksbehandler(id, "Z123456", "Test", "flere@nav.no", "3450", listOf(Områder.K9), kode6, tidspunkt)
            )

            val lagret = repository.finnSaksbehandlerMedId(id)!!
            assertThat(lagret.skjermet, equalTo(kode6))
            assertThat(lagret.områder.toSet(), equalTo(setOf(Områder.K9, Områder.AKTIVITETSPENGER)))
            assertThat(lagret.sistOppdatert, equalTo(tidspunkt))
            Områder.entries.forEach { område ->
                assertThat(repository.hentAlleSaksbehandlere(område, kode6).any { it.id == id }, equalTo(true))
                assertThat(repository.hentAlleSaksbehandlere(område, !kode6).any { it.id == id }, equalTo(false))
            }
        }
    }

    @Test
    fun `ukjent id returnerer null`() {
        assertThat(get<SaksbehandlerRepository>().finnSaksbehandlerMedId(Long.MAX_VALUE), equalTo(null))
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
            saksbehandlerRepository.finnSaksbehandlerMedIdent(ident2, skjermet = false)
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
