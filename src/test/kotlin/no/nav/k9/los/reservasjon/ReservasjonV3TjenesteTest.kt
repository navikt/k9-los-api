package no.nav.k9.los.reservasjon

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.feilhandtering.FinnerIkkeDataException
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.equalsWithPrecision
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler

class ReservasjonV3TjenesteTest : AbstractK9LosIntegrationTest() {
    private lateinit var saksbehandlerInnlogget: Saksbehandler
    private lateinit var saksbehandler1: Saksbehandler

    @BeforeEach
    fun setup() {
        val testSaksbehandlerRepository = get<TestSaksbehandlerRepository>()

        saksbehandlerInnlogget = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "saksbehandler@nav.no",
                    enhet = null,
                    områder = listOf(Områder.K9)
                )
            )
        }

        saksbehandler1 = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test1@test.no",
                    enhet = null,
                    områder = listOf(Områder.K9)
                )
            )
        }
    }

    @Test
    fun `ta reservasjon`() = runTest {
        val transactionalManager = get<TransactionalManager>()
        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()

        val reservasjon = transactionalManager.transactionSuspend { tx ->
            reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
                område = Områder.K9,
                reservasjonsnøkkel = "test1",
                reserverForId = saksbehandler1.id,
                kommentar = "",
                gyldigFra = LocalDateTime.now(),
                gyldigTil = LocalDateTime.now().plusDays(1),
                utføresAvId = saksbehandlerInnlogget.id,
                tx = tx
            )
        }


        assertTrue(reservasjon.reservertAv == saksbehandler1.id)
        assertFalse(reservasjon.reservertAv == saksbehandlerInnlogget.id)
        assertTrue(reservasjon.gyldigTil.isAfter(LocalDateTime.now()))
        assertEquals("test1", reservasjon.reservasjonsnøkkel)

        val reservasjonerV3MedOppgaver =
            reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(Områder.K9,saksbehandler1.id)

        assertEquals(1, reservasjonerV3MedOppgaver.size)
        assertEquals(saksbehandler1.id, reservasjonerV3MedOppgaver[0].reservasjonV3.reservertAv)
    }


    @Test
    fun `annullerReservasjon`() = runTest {
        val transactionalManager = get<TransactionalManager>()
        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()

        transactionalManager.transactionSuspend { tx ->
            reservasjonV3Tjeneste.forsøkReservasjonOgReturnerAktiv(
                område = Områder.K9,
                reservasjonsnøkkel = "test1",
                reserverForId = saksbehandler1.id,
                kommentar = "",
                gyldigFra = LocalDateTime.now(),
                gyldigTil = LocalDateTime.now().plusDays(1),
                utføresAvId = saksbehandlerInnlogget.id,
                tx = tx
            )
        }

        reservasjonV3Tjeneste.annullerReservasjonHvisFinnes(
            Områder.K9,
            reservasjonsnøkkel = "test1",
            "",
            annullertAvBrukerId = saksbehandlerInnlogget.id
        )

        val aktiveReservasjoner =
            reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(Områder.K9,saksbehandler1.id)

        assertEquals(0, aktiveReservasjoner.size)
    }


    @Test
    fun `overføre reservasjon`() {
        val repo = get<ReservasjonV3Repository>()
        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()
        val transactionalManager = get<TransactionalManager>()
        val testSaksbehandlerRepository = get<TestSaksbehandlerRepository>()

        val saksbehandler2 = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "test2@test.no",
                    enhet = null,
                    områder = listOf(Områder.K9)
                )
            )
        }

        val reservasjon = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            kommentar = "",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            endretAv = null
        )

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(Områder.K9, reservasjon, tx)
        }

        val overførTildato = LocalDateTime.now().plusDays(2)

        reservasjonV3Tjeneste.overførReservasjon(
            Områder.K9,
            "test1",
            overførTildato,
            saksbehandler2.id,
            saksbehandler2.id,
            ""
        )

        transactionalManager.transaction { tx ->
            val reservasjonHentet = repo.hentAktivReservasjonForReservasjonsnøkkel(Områder.K9, "test1", tx)
            assertEquals(saksbehandler2.id, reservasjonHentet!!.reservertAv)
            assertTrue(overførTildato.equalsWithPrecision(reservasjonHentet.gyldigTil, 10))
            assertEquals(reservasjon.reservasjonsnøkkel, reservasjonHentet.reservasjonsnøkkel)
        }

        transactionalManager.transaction { tx ->
            val reservasjonerHentet = repo.hentAktiveReservasjonerForSaksbehandler(Områder.K9, saksbehandler2.id, tx)
            assertEquals(saksbehandler2.id, reservasjonerHentet[0].reservertAv)
            assertTrue(overførTildato.equalsWithPrecision(reservasjonerHentet[0].gyldigTil, 10))
            assertEquals(reservasjon.reservasjonsnøkkel, reservasjonerHentet[0].reservasjonsnøkkel)
        }
    }

    @Test
    fun `overføre reservasjon til saksbehandler som ikke finnes`() {
        val repo = get<ReservasjonV3Repository>()
        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()
        val transactionalManager = get<TransactionalManager>()

        val reservasjon = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            kommentar = "",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            endretAv = null
        )

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(Områder.K9, reservasjon, tx)
        }

        val overførTildato = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.MICROS)

        assertThrows<IllegalArgumentException> {
            reservasjonV3Tjeneste.overførReservasjon(
                Områder.K9,
                reservasjon.reservasjonsnøkkel,
                overførTildato,
                5L,
                saksbehandler1.id,
                ""
            )
        }

        transactionalManager.transaction { tx ->
            val reservasjonHentet = repo.hentAktivReservasjonForReservasjonsnøkkel(Områder.K9, "test1", tx)
            assertEquals(saksbehandler1.id, reservasjonHentet!!.reservertAv)
            assertTrue(reservasjon.gyldigTil.equals(reservasjonHentet.gyldigTil))
            assertEquals(reservasjon.reservasjonsnøkkel, reservasjonHentet.reservasjonsnøkkel)
        }
    }

    @Test
    fun `overføre eller endre reservasjon til saksbehandler i annet område feiler`() {
        get<OmrådeRepository>().lagre(Områder.AKTIVITETSPENGER)
        val repo = get<ReservasjonV3Repository>()
        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()
        val transactionalManager = get<TransactionalManager>()
        val testSaksbehandlerRepository = get<TestSaksbehandlerRepository>()

        val aktSaksbehandler = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = null,
                    navn = null,
                    epost = "akt@test.no",
                    enhet = null,
                    områder = listOf(Områder.AKTIVITETSPENGER)
                )
            )
        }

        val reservasjon = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            kommentar = "",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            endretAv = null
        )
        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(Områder.K9, reservasjon, tx)
        }

        assertThrows<FinnerIkkeDataException> {
            reservasjonV3Tjeneste.overførReservasjon(
                Områder.K9, "test1", LocalDateTime.now().plusDays(2), aktSaksbehandler.id, saksbehandler1.id, ""
            )
        }
        assertThrows<FinnerIkkeDataException> {
            reservasjonV3Tjeneste.endreReservasjon(
                Områder.K9, "test1", saksbehandler1.id, null, aktSaksbehandler.id, null
            )
        }

        transactionalManager.transaction { tx ->
            val reservasjonHentet = repo.hentAktivReservasjonForReservasjonsnøkkel(Områder.K9, "test1", tx)
            assertEquals(saksbehandler1.id, reservasjonHentet!!.reservertAv)
        }
    }

}