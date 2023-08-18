package no.nav.k9.los.nyoppgavestyring.mottak.reservasjon

import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import java.time.LocalDateTime

class ReservasjonV3RepositoryTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `Teste skriv og les`() {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        val reservasjonV3Repository = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()

        val saksbehandler = runBlocking {
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

        val test = TaReservasjonDto("asd", "asdf", LocalDateTime.now(), LocalDateTime.now().plusDays(1))
        val reservasjon = ReservasjonV3(
            reservertAv = saksbehandler.id!!,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
        )

        transactionalManager.transaction { tx ->
            reservasjonV3Repository.lagreReservasjon(reservasjon, tx)
        }

        transactionalManager.transaction { tx ->
            val reservasjonHentet = reservasjonV3Repository.hentAktivReservasjonForReservasjonsnøkkel("test1", tx)
            assertEquals(reservasjon, reservasjonHentet)
        }

        transactionalManager.transaction { tx ->
            val reservasjonerHentet = reservasjonV3Repository.hentAktiveReservasjonerForSaksbehandler(saksbehandler, tx)
            assertEquals(reservasjon, reservasjonerHentet[0])
        }
    }

    @Test
    fun `tillatt med 2 reservasjoner på samme nøkkel med ikke overlappende gyldig tidsrom`() {
        val repo = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val saksbehandler1 = runBlocking {
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

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id!!,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().minusDays(5),
            gyldigTil = LocalDateTime.now().minusDays(1),
        )

        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler2.id!!,
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
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val saksbehandler1 = runBlocking {
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

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id!!,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().minusDays(5),
            gyldigTil = LocalDateTime.now().minusDays(1),
        )

        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler1.id!!,
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
                repo.hentAktiveReservasjonerForSaksbehandler(saksbehandler1, tx)
            assertEquals(reservasjon2, aktiveReservasjoner[0])
        }
    }

    @Test
    fun `Ikke tillatt med 2 reservasjoner på samme nøkkel med overlappende gyldig tidsrom`() {
        val repo = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val saksbehandler1 = runBlocking {
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

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id!!,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
        )

        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler2.id!!,
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
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val saksbehandler1 = runBlocking {
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

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id!!,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
        )

        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler2.id!!,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().plusMinutes(1),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
        )

        val saksbehandlerInnlogget = runBlocking {
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

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon1, tx)
            repo.annullerAktivReservasjonOgLagreEndring(saksbehandler1, saksbehandlerInnlogget, reservasjon1.reservasjonsnøkkel, tx)
        }

        transactionalManager.transaction { tx ->
            repo.lagreReservasjon(reservasjon2, tx)
        }
    }
}