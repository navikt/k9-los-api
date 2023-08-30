package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.lager.oppgave.v2.equalsWithPrecision
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

class ReservasjonV3TjenesteTest : AbstractK9LosIntegrationTest() {
    private lateinit var saksbehandlerInnlogget: Saksbehandler
    private lateinit var saksbehandler1: Saksbehandler

    @BeforeEach
    fun setup() {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        saksbehandlerInnlogget = runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    brukerIdent = "saksbehandler@nav.no",
                    navn = null,
                    epost = "saksbehandler@nav.no",
                    reservasjoner = mutableSetOf(),
                    enhet = null,
                )
            )
            saksbehandlerRepository.finnSaksbehandlerMedEpost("saksbehandler@nav.no")!!
        }

        saksbehandler1 = runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    brukerIdent = null,
                    navn = null,
                    epost = "test1@test.no",
                    reservasjoner = mutableSetOf(),
                    enhet = null,
                )
            )
            saksbehandlerRepository.finnSaksbehandlerMedEpost("test1@test.no")!!
        }
    }

    @Test
    fun `ta reservasjon`() {
        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()

        val reservasjon = runBlocking {
            reservasjonV3Tjeneste.taReservasjon(
                reservasjonsnøkkel = "test1",
                reserverForId = saksbehandler1.id!!,
                gyldigFra = LocalDateTime.now(),
                gyldigTil = LocalDateTime.now().plusDays(1),
                utføresAvId = saksbehandlerInnlogget.id!!
            )
        }

        assertTrue(reservasjon.reservertAv == saksbehandler1.id)
        assertFalse(reservasjon.reservertAv == saksbehandlerInnlogget.id)
        assertTrue(reservasjon.gyldigTil.isAfter(LocalDateTime.now()))
        assertEquals("test1", reservasjon.reservasjonsnøkkel)

        val reservasjoner =
            reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(saksbehandler1.id!!)

        assertEquals(1, reservasjoner.size)
        assertEquals(saksbehandler1.id, reservasjoner[0].reservertAv)
    }


    @Test
    fun `annullerReservasjon`() {
        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()

        val reservasjon = runBlocking {
            reservasjonV3Tjeneste.taReservasjon(
                reservasjonsnøkkel = "test1",
                reserverForId = saksbehandler1.id!!,
                gyldigFra = LocalDateTime.now(),
                gyldigTil = LocalDateTime.now().plusDays(1),
                utføresAvId = saksbehandlerInnlogget.id!!
            )
        }

        reservasjonV3Tjeneste.annullerReservasjon(reservasjonsnøkkel = "test1", annullertAvBrukerId = saksbehandlerInnlogget.id!!)

        val aktiveReservasjoner =
            reservasjonV3Tjeneste.hentReservasjonerForSaksbehandler(saksbehandler1.id!!)

        assertEquals(0, aktiveReservasjoner.size)
    }


    @Test
    fun `overføre reservasjon`() {
        val repo = get<ReservasjonV3Repository>()
        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()
        val transactionalManager = get<TransactionalManager>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val saksbehandler2 = runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    brukerIdent = null,
                    navn = null,
                    epost = "test2@test.no",
                    reservasjoner = mutableSetOf(),
                    enhet = null,
                )
            )
            saksbehandlerRepository.finnSaksbehandlerMedEpost("test2@test.no")!!
        }

        val reservasjon = ReservasjonV3(
            reservertAv = saksbehandler1.id!!,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
        )

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon, tx)
        }

        val overførTildato = LocalDateTime.now().plusDays(2)

        reservasjonV3Tjeneste.overførReservasjon(
            "test1",
            overførTildato,
            saksbehandler2.id!!,
            saksbehandler2.id!!
        )

        transactionalManager.transaction { tx ->
            val reservasjonHentet = repo.hentAktivReservasjonForReservasjonsnøkkel("test1", tx)
            assertEquals(saksbehandler2.id, reservasjonHentet!!.reservertAv)
            assertTrue(overførTildato.equalsWithPrecision(reservasjonHentet.gyldigTil, 10))
            assertEquals(reservasjon.reservasjonsnøkkel, reservasjonHentet.reservasjonsnøkkel)
        }

        transactionalManager.transaction { tx ->
            val reservasjonerHentet = repo.hentAktiveReservasjonerForSaksbehandler(saksbehandler2.id!!, tx)
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

        var reservasjon = ReservasjonV3(
            reservertAv = saksbehandler1.id!!,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
        )

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon, tx)
        }

        val overførTildato = LocalDateTime.now().plusDays(2).truncatedTo(ChronoUnit.MICROS)

        assertThrows<IllegalArgumentException> {
            reservasjonV3Tjeneste.overførReservasjon(
                reservasjon.reservasjonsnøkkel,
                overførTildato,
                5L,
                saksbehandler1.id!!
            )
        }

        transactionalManager.transaction { tx ->
            val reservasjonHentet = repo.hentAktivReservasjonForReservasjonsnøkkel("test1", tx)
            assertEquals(saksbehandler1.id, reservasjonHentet!!.reservertAv)
            assertTrue(reservasjon.gyldigTil.equals(reservasjonHentet.gyldigTil))
            assertEquals(reservasjon.reservasjonsnøkkel, reservasjonHentet.reservasjonsnøkkel)
        }
    }

}