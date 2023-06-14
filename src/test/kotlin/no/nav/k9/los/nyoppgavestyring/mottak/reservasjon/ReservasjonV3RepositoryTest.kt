package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.lager.oppgave.v2.equalsWithPrecision
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import java.time.LocalDateTime

class ReservasjonV3RepositoryTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `Teste skriv og les`() {
        val repo = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()
        var reservasjon = ReservasjonV3(
            saksbehandlerEpost = "test1@test.com",
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
        )

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon, tx)
        }

        transactionalManager.transaction { tx ->
            val reservasjonHentet = repo.hentAktivReservasjonForReservasjonsnøkkel("test1", tx)
            assertEquals(reservasjon, reservasjonHentet)
        }

        transactionalManager.transaction { tx ->
            val reservasjonerHentet = repo.hentAktiveReservasjonerForSaksbehandler("test1@test.com", tx)
            assertEquals(reservasjon, reservasjonerHentet[0])
        }
    }

    @Test
    fun `tillatt med 2 reservasjoner på samme nøkkel med ikke overlappende gyldig tidsrom`() {
        val repo = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()
        var reservasjon1 = ReservasjonV3(
            saksbehandlerEpost = "test1@test.com",
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().minusDays(5),
            gyldigTil = LocalDateTime.now().minusDays(1),
        )

        var reservasjon2 = ReservasjonV3(
            saksbehandlerEpost = "test2@test.com",
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().plusMinutes(1),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
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
        var reservasjon1 = ReservasjonV3(
            saksbehandlerEpost = "test1@test.com",
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().minusDays(5),
            gyldigTil = LocalDateTime.now().minusDays(1),
        )

        var reservasjon2 = ReservasjonV3(
            saksbehandlerEpost = "test1@test.com",
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
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
                repo.hentAktiveReservasjonerForSaksbehandler(reservasjon1.saksbehandlerEpost, tx)
            assertEquals(reservasjon2, aktiveReservasjoner[0])
        }
    }

    @Test
    fun `Ikke tillatt med 2 reservasjoner på samme nøkkel med overlappende gyldig tidsrom`() {
        val repo = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()
        var reservasjon1 = ReservasjonV3(
            saksbehandlerEpost = "test1@test.com",
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
        )

        var reservasjon2 = ReservasjonV3(
            saksbehandlerEpost = "test2@test.com",
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().plusMinutes(1),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
        )

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon1, tx)
        }

        val exception =
            assertThrows<IllegalArgumentException> {
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
        var reservasjon1 = ReservasjonV3(
            saksbehandlerEpost = "test1@test.com",
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
        )

        var reservasjon2 = ReservasjonV3(
            saksbehandlerEpost = "test2@test.com",
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().plusMinutes(1),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
        )

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon1, tx)
            repo.annullerAktivReservasjon(AnnullerReservasjonDto(reservasjon1.saksbehandlerEpost, reservasjon1.reservasjonsnøkkel), tx)
        }

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon2, tx)
        }
    }

    @Test
    fun `overføre reservasjon`() {
        val repo = get<ReservasjonV3Repository>()
        val reservasjonV3Tjeneste = get<ReservasjonV3Tjeneste>()
        val transactionalManager = get<TransactionalManager>()
        var reservasjon = ReservasjonV3(
            saksbehandlerEpost = "test1@test.com",
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
        )

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon, tx)
        }

        val overførTildato = LocalDateTime.now().plusDays(2)

        reservasjonV3Tjeneste.overførReservasjon(OverførReservasjonDto(reservasjon.saksbehandlerEpost, "test2@test.com", reservasjon.reservasjonsnøkkel, overførTildato))

        transactionalManager.transaction { tx ->
            val reservasjonHentet = repo.hentAktivReservasjonForReservasjonsnøkkel("test1", tx)
            assertEquals("test2@test.com", reservasjonHentet!!.saksbehandlerEpost)
            assertTrue(overførTildato.equalsWithPrecision(reservasjonHentet!!.gyldigTil, 10))
            assertEquals(reservasjon.reservasjonsnøkkel, reservasjonHentet!!.reservasjonsnøkkel)
        }

        transactionalManager.transaction { tx ->
            val reservasjonerHentet = repo.hentAktiveReservasjonerForSaksbehandler("test2@test.com", tx)
            assertEquals("test2@test.com", reservasjonerHentet[0]!!.saksbehandlerEpost)
            assertTrue(overførTildato.equalsWithPrecision(reservasjonerHentet[0]!!.gyldigTil, 10))
            assertEquals(reservasjon.reservasjonsnøkkel, reservasjonerHentet[0]!!.reservasjonsnøkkel)
        }
    }
}