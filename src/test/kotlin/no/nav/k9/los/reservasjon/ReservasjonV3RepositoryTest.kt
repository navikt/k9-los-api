package no.nav.k9.los.reservasjon

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import java.time.LocalDateTime
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler

class ReservasjonV3RepositoryTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `Teste skriv og les`() {
        val testSaksbehandlerRepository = get<TestSaksbehandlerRepository>()
        val reservasjonV3Repository = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()

        val saksbehandler = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test1@test.no",
                    enhet = null,
                )
            )
        }

        val reservasjon = ReservasjonV3(
            reservertAv = saksbehandler.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null
        )

        transactionalManager.transaction { tx ->
            reservasjonV3Repository.lagreReservasjon(reservasjon, tx)
        }

        transactionalManager.transaction { tx ->
            val reservasjonHentet = reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel("test1", tx)
            assertEquals(reservasjon, reservasjonHentet)
        }

        transactionalManager.transaction { tx ->
            val reservasjonerHentet =
                reservasjonV3Repository.hentAktiveReservasjonerForSaksbehandler(saksbehandler.id, tx)
            assertEquals(reservasjon, reservasjonerHentet[0])
        }
    }

    @Test
    fun `tillatt med 2 reservasjoner på samme nøkkel med ikke overlappende gyldig tidsrom`() {
        val repo = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()
        val testSaksbehandlerRepository = get<TestSaksbehandlerRepository>()

        val saksbehandler1 = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test1@test.no",
                    enhet = null,
                )
            )
        }

        val saksbehandler2 = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test2@test.no",
                    enhet = null,
                )
            )
        }

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().minusDays(5),
            gyldigTil = LocalDateTime.now().minusDays(1),
            kommentar = "",
            endretAv = null
        )

        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler2.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().plusMinutes(1),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
            kommentar = "",
            endretAv = null
        )

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon1, tx)
        }

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon2, tx)
        }
    }

    @Test
    fun `hent kun aktiv reservasjon`() {
        val repo = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()
        val testSaksbehandlerRepository = get<TestSaksbehandlerRepository>()

        val saksbehandler1 = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test1@test.no",
                    enhet = null,
                )
            )
        }

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().minusDays(5),
            gyldigTil = LocalDateTime.now().minusDays(1),
            kommentar = "",
            endretAv = null
        )

        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
            kommentar = "",
            endretAv = null
        )

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon1, tx)
            repo.lagreReservasjon(reservasjon2, tx)
        }

        transactionalManager.transaction { tx ->
            val aktivReservasjon =
                repo.hentAktivReservasjonForReservasjonsnøkkel(reservasjon1.reservasjonsnøkkel, tx)
            assertEquals(reservasjon2, aktivReservasjon)
        }


        transactionalManager.transaction { tx ->
            val aktiveReservasjoner =
                repo.hentAktiveReservasjonerForSaksbehandler(saksbehandler1.id, tx)
            assertEquals(reservasjon2, aktiveReservasjoner[0])
        }
    }

    @Test
    fun `Ikke tillatt med 2 reservasjoner på samme nøkkel med overlappende gyldig tidsrom`() {
        val repo = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()
        val testSaksbehandlerRepository = get<TestSaksbehandlerRepository>()

        val saksbehandler1 = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test1@test.no",
                    enhet = null,
                )
            )
        }

        val saksbehandler2 = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test2@test.no",
                    enhet = null,
                )
            )
        }

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null
        )

        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler2.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().plusMinutes(1),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
            kommentar = "",
            endretAv = null
        )

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon1, tx)
        }

        val exception =
            assertThrows<AlleredeReservertException> {
                transactionalManager.transaction { tx ->
                    repo.lagreReservasjon(reservasjon2, tx)
                }
            }

        assertTrue(exception.message!!.contains("er allerede reservert"))
    }

    @Test
    fun `Ikke tillatt med 2 reservasjoner på samme nøkkel med overlappende gyldig tidsrom med mindre alle unntatt 1 er annullert`() {
        val repo = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()
        val testSaksbehandlerRepository = get<TestSaksbehandlerRepository>()

        val saksbehandler1 = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test1@test.no",
                    enhet = null,
                )
            )
        }

        val saksbehandler2 = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test2@test.no",
                    enhet = null,
                )
            )
        }

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null
        )

        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler2.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().plusMinutes(1),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
            kommentar = "",
            endretAv = null
        )

        val saksbehandlerInnlogget = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "saksbehandler@nav.no",
                    enhet = null,
                )
            )
        }

        transactionalManager.transaction { tx ->
            val reservasjon = repo.lagreReservasjon(reservasjon1, tx)
            repo.annullerAktivReservasjonOgLagreEndring(reservasjon, "", saksbehandlerInnlogget.id, tx)
        }

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon2, tx)
        }
    }

    @Test
    fun `forleng reservasjon`() {
        val testSaksbehandlerRepository = get<TestSaksbehandlerRepository>()
        val reservasjonV3Repository = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()

        val saksbehandler = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test1@test.no",
                    enhet = null,
                )
            )
        }

        val reservasjon = ReservasjonV3(
            reservertAv = saksbehandler.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null
        )

        transactionalManager.transaction { tx ->
            reservasjonV3Repository.lagreReservasjon(reservasjon, tx)
        }

        val forlengetReservasjon = transactionalManager.transaction { tx ->
            val hentetReservasjon =
                reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel("test1", tx)!!
            reservasjonV3Repository.forlengReservasjon(
                hentetReservasjon,
                1,
                hentetReservasjon.gyldigTil.plusDays(1),
                "testkommentar",
                tx
            )
        }

        assertThat(forlengetReservasjon.gyldigTil).isEqualTo(reservasjon.gyldigTil.plusDays(1))
        assertThat(forlengetReservasjon.kommentar).isEqualTo("testkommentar")
    }

    @Test
    fun `Skal hente riktig antall reservasjoner for saksbehandlerne, og for kun de saksbehandlerne som er i lista`() {
        val testSaksbehandlerRepository = get<TestSaksbehandlerRepository>()
        val reservasjonV3Repository = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()

        val saksbehandler1 = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test1@test.no",
                    enhet = null,
                )
            )
        }
        val saksbehandler2 = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test2@test.no",
                    enhet = null,
                )
            )
        }
        val saksbehandler3skjermet = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test3@test.no",
                    enhet = null,
                )
            )
        }

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null
        )
        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test2",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null
        )
        val reservasjon3 = ReservasjonV3(
            reservertAv = saksbehandler2.id,
            reservasjonsnøkkel = "test3",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null
        )
        val reservasjon4 = ReservasjonV3(
            reservertAv = saksbehandler3skjermet.id,
            reservasjonsnøkkel = "test4",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null
        )

        transactionalManager.transaction { tx ->
            reservasjonV3Repository.lagreReservasjon(reservasjon1,tx)
            reservasjonV3Repository.lagreReservasjon(reservasjon2,tx)
            reservasjonV3Repository.lagreReservasjon(reservasjon3,tx)
            reservasjonV3Repository.lagreReservasjon(reservasjon4,tx)
        }

        val resultat = transactionalManager.transaction { tx ->
            reservasjonV3Repository.tellAktiveReservasjonerForSaksbehandlere(
                setOf(
                    saksbehandler1.id,
                    saksbehandler2.id
                ), tx
            )
        }

        assertEquals(resultat, mapOf(
            saksbehandler1.id to 2,
            saksbehandler2.id to 1
        ))
    }
}