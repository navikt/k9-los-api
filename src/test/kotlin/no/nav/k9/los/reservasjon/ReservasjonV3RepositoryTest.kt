package no.nav.k9.los.reservasjon

import assertk.assertThat
import assertk.assertions.isEqualTo
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.infrastruktur.db.TransactionalManager
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import java.time.LocalDateTime
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgavedefinisjon.omraade.OmrådeRepository

class ReservasjonV3RepositoryTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `Teste skriv og les`() {
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        val reservasjonV3Repository = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()

        val saksbehandler = runBlocking {
            saksbehandlerRepository.opprettSaksbehandler("test1@test.no", Områder.K9)

            saksbehandlerRepository.finnSaksbehandlerMedEpost("test1@test.no", skjermet = false)!!
        }

        val reservasjon = ReservasjonV3(
            reservertAv = saksbehandler.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
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
                reservasjonV3Repository.hentAktiveReservasjonerForSaksbehandler(saksbehandler.id, Områder.K9, tx)
            assertEquals(reservasjon, reservasjonerHentet[0])
        }
    }

    @Test
    fun `tillatt med 2 reservasjoner på samme nøkkel med ikke overlappende gyldig tidsrom`() {
        val repo = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val saksbehandler1 = runBlocking {
            saksbehandlerRepository.opprettSaksbehandler("test1@test.no", Områder.K9)

            saksbehandlerRepository.finnSaksbehandlerMedEpost("test1@test.no", skjermet = false)!!
        }

        val saksbehandler2 = runBlocking {
            saksbehandlerRepository.opprettSaksbehandler("test2@test.no", Områder.K9)
            saksbehandlerRepository.finnSaksbehandlerMedEpost("test2@test.no", skjermet = false)!!
        }

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().minusDays(5),
            gyldigTil = LocalDateTime.now().minusDays(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
        )

        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler2.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().plusMinutes(1),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
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
            saksbehandlerRepository.opprettSaksbehandler("test1@test.no", Områder.K9)

            saksbehandlerRepository.finnSaksbehandlerMedEpost("test1@test.no", skjermet = false)!!
        }

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().minusDays(5),
            gyldigTil = LocalDateTime.now().minusDays(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
        )

        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
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
                repo.hentAktiveReservasjonerForSaksbehandler(saksbehandler1.id, Områder.K9, tx)
            assertEquals(reservasjon2, aktiveReservasjoner[0])
        }
    }

    @Test
    fun `Ikke tillatt med 2 reservasjoner på samme nøkkel med overlappende gyldig tidsrom`() {
        val repo = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val saksbehandler1 = runBlocking {
            saksbehandlerRepository.opprettSaksbehandler("test1@test.no", Områder.K9)

            saksbehandlerRepository.finnSaksbehandlerMedEpost("test1@test.no", skjermet = false)!!
        }

        val saksbehandler2 = runBlocking {
            saksbehandlerRepository.opprettSaksbehandler("test2@test.no", Områder.K9)
            saksbehandlerRepository.finnSaksbehandlerMedEpost("test2@test.no", skjermet = false)!!
        }

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
        )

        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler2.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().plusMinutes(1),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
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
        val saksbehandlerRepository = get<SaksbehandlerRepository>()

        val saksbehandler1 = runBlocking {
            saksbehandlerRepository.opprettSaksbehandler("test1@test.no", Områder.K9)

            saksbehandlerRepository.finnSaksbehandlerMedEpost("test1@test.no", skjermet = false)!!
        }

        val saksbehandler2 = runBlocking {
            saksbehandlerRepository.opprettSaksbehandler("test2@test.no", Områder.K9)
            saksbehandlerRepository.finnSaksbehandlerMedEpost("test2@test.no", skjermet = false)!!
        }

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
        )

        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler2.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now().plusMinutes(1),
            gyldigTil = LocalDateTime.now().plusDays(1).plusMinutes(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
        )

        val saksbehandlerInnlogget = runBlocking {
            saksbehandlerRepository.opprettSaksbehandler("saksbehandler@nav.no", Områder.K9)
            saksbehandlerRepository.finnSaksbehandlerMedEpost("saksbehandler@nav.no", skjermet = false)!!
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
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        val reservasjonV3Repository = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()

        val saksbehandler = runBlocking {
            saksbehandlerRepository.opprettSaksbehandler("test1@test.no", Områder.K9)

            saksbehandlerRepository.finnSaksbehandlerMedEpost("test1@test.no", skjermet = false)!!
        }

        val reservasjon = ReservasjonV3(
            reservertAv = saksbehandler.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
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
        val saksbehandlerRepository = get<SaksbehandlerRepository>()
        val reservasjonV3Repository = get<ReservasjonV3Repository>()
        val transactionalManager = get<TransactionalManager>()

        val saksbehandler1 = runBlocking {
            saksbehandlerRepository.opprettSaksbehandler("test1@test.no", Områder.K9)
            saksbehandlerRepository.finnSaksbehandlerMedEpost("test1@test.no", skjermet = false)!!
        }
        val saksbehandler2 = runBlocking {
            saksbehandlerRepository.opprettSaksbehandler("test2@test.no", Områder.K9)
            saksbehandlerRepository.finnSaksbehandlerMedEpost("test2@test.no", skjermet = false)!!
        }
        val saksbehandler3skjermet = runBlocking {
            saksbehandlerRepository.opprettSaksbehandler("test3@test.no", Områder.K9)
            saksbehandlerRepository.finnSaksbehandlerMedEpost("test3@test.no", skjermet = false)!!
        }

        val reservasjon1 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test1",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
        )
        val reservasjon2 = ReservasjonV3(
            reservertAv = saksbehandler1.id,
            reservasjonsnøkkel = "test2",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
        )
        val reservasjon3 = ReservasjonV3(
            reservertAv = saksbehandler2.id,
            reservasjonsnøkkel = "test3",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
        )
        val reservasjon4 = ReservasjonV3(
            reservertAv = saksbehandler3skjermet.id,
            reservasjonsnøkkel = "test4",
            gyldigFra = LocalDateTime.now(),
            gyldigTil = LocalDateTime.now().plusDays(1),
            kommentar = "",
            endretAv = null,
            område = Områder.K9
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
                ), Områder.K9, tx
            )
        }

        assertEquals(resultat, mapOf(
            saksbehandler1.id to 2,
            saksbehandler2.id to 1
        ))
    }

    @Test
    fun `telling og lister avgrenses til området for saksbehandler med reservasjoner i flere områder`() {
        val områder = get<OmrådeRepository>()
        Områder.entries.forEach { områder.lagre(it.eksternId) }
        val saksbehandlere = get<SaksbehandlerRepository>()
        val saksbehandlerId = saksbehandlere.opprettSaksbehandler("fleromrade@nav.no", Områder.K9)
        saksbehandlere.leggTilOmråde(saksbehandlerId, Områder.AKTIVITETSPENGER)
        val repository = get<ReservasjonV3Repository>()
        val tjeneste = get<ReservasjonV3Tjeneste>()
        val nå = LocalDateTime.now()
        val forventedeNøkler = mapOf(
            Områder.K9 to setOf("k9-1", "k9-2"),
            Områder.AKTIVITETSPENGER to setOf("aktivitetspenger-1"),
        )

        get<TransactionalManager>().transaction { tx ->
            forventedeNøkler.forEach { (område, nøkler) ->
                nøkler.forEach { nøkkel ->
                    repository.lagreReservasjon(ReservasjonV3(saksbehandlerId, nøkkel, "", nå, nå.plusDays(1), null, område), tx)
                }
            }

            forventedeNøkler.forEach { (område, nøkler) ->
                assertThat(tjeneste.tellReservasjonerForSaksbehandlere(setOf(saksbehandlerId), område, tx))
                    .isEqualTo(mapOf(saksbehandlerId to nøkler.size))
                assertThat(repository.hentAktiveReservasjonerForSaksbehandler(saksbehandlerId, område, tx).map { it.reservasjonsnøkkel }.toSet())
                    .isEqualTo(nøkler)
                assertThat(repository.hentAlleAktiveReservasjoner(område, tx).map { it.reservasjonsnøkkel }.toSet())
                    .isEqualTo(nøkler)
                assertThat(repository.tellAktiveReservasjonerForSaksbehandlere(emptySet(), område, tx))
                    .isEqualTo(emptyMap())
            }
        }
        forventedeNøkler.forEach { (område, nøkler) ->
            assertThat(tjeneste.hentReservasjonerForSaksbehandler(saksbehandlerId, område).map { it.reservasjonV3.reservasjonsnøkkel }.toSet())
                .isEqualTo(nøkler)
            assertThat(tjeneste.hentAlleAktiveReservasjoner(område).map { it.reservasjonV3.reservasjonsnøkkel }.toSet())
                .isEqualTo(nøkler)
        }
    }
}
