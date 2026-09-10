package no.nav.k9.los.uttrekk

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.runBlocking
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.lagretsok.LagretSøk
import no.nav.k9.los.lagretsok.LagretSøkRepository
import no.nav.k9.los.lagretsok.NyttLagretSøkRequest
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.oppgaveuthenting.query.OppgaveQueryService
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import kotliquery.queryOf
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import java.time.LocalDateTime

class UttrekkRepositoryTest : AbstractK9LosIntegrationTest() {

    private lateinit var uttrekkRepository: UttrekkRepository
    private lateinit var lagretSøkRepository: LagretSøkRepository
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository
    private lateinit var testSaksbehandlerRepository: TestSaksbehandlerRepository
    private var saksbehandlerId: Long = 0L
    private lateinit var testQuery: OppgaveQuery
    private lateinit var testLagretSøk: LagretSøk

    @BeforeEach
    fun setup() {
        uttrekkRepository = get()
        lagretSøkRepository = get()
        saksbehandlerRepository = get()
        testSaksbehandlerRepository = get()

        runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(navident = "test", navn = "Test Testersen", epost = "test@nav.no", enhet = null),
                Områder.K9,
                skjermet = false,
            )
            val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost("test@nav.no", skjermet = false)!!
            saksbehandlerId = saksbehandler.id
            val lagretSøk = LagretSøk.nyttSøk(
                NyttLagretSøkRequest(tittel = "Test søk", query = LagretSøk.defaultQuery(Områder.K9, false)),
                saksbehandler, Områder.K9,
            )
            val søkId = lagretSøkRepository.opprett(lagretSøk)
            testQuery = lagretSøk.query
            testLagretSøk = lagretSøkRepository.hent(søkId)!!
        }
    }

    @Test
    fun `sletting via kildesøk respekterer uttrekkets lagrede beskyttelsesnivå`() {
        val tjeneste = UttrekkTjeneste(uttrekkRepository, lagretSøkRepository)
        val søkId = requireNotNull(testLagretSøk.id)
        val ugradert = tjeneste.opprett(OpprettUttrekk(søkId, "Test", null, null), saksbehandlerId, Områder.K9, false)
        val kode6 = tjeneste.opprett(OpprettUttrekk(søkId, "Test", null, null), saksbehandlerId, Områder.K9, true)
        assertThat(tjeneste.slettForLagretSøk(søkId, saksbehandlerId, Områder.K9, false)).isEqualTo(1)
        assertThat(uttrekkRepository.hent(ugradert)).isNull()
        assertThat(uttrekkRepository.hent(kode6)).isNotNull()
    }

    @Test
    fun `delvis eller ugyldig tilgangsmetadata må ikke få legacy defaults`() {
        val id = uttrekkRepository.opprett(Uttrekk.opprettUttrekk(testLagretSøk, saksbehandlerId, false))
        for (metadata in listOf("null", "{}", """{"område":"K9","harTilgangTilKode6":"false"}""")) {
            uttrekkRepository.transactionalManager.transaction { tx ->
                tx.run(queryOf("UPDATE uttrekk SET query = jsonb_set(query, '{_uttrekkTilgang}', :metadata::jsonb) WHERE id = :id",
                    mapOf("metadata" to metadata, "id" to id)).asUpdate)
            }
            assertThrows<IllegalArgumentException> { uttrekkRepository.hent(id) }
        }
    }

    @Test
    fun `lagret område og beskyttelsesnivå overlever slettet kildesøk og jobbkjøring`() {
        val tjeneste = UttrekkTjeneste(uttrekkRepository, lagretSøkRepository)
        val ids = listOf(false, true).map { kode6 ->
            tjeneste.opprett(OpprettUttrekk(requireNotNull(testLagretSøk.id), "Test", null, null), saksbehandlerId, Områder.K9, kode6)
        }
        uttrekkRepository.transactionalManager.transactionContext {
            lagretSøkRepository.slett(testLagretSøk)
        }

        ids.zip(listOf(false, true)).forEach { (id, kode6) ->
            val hentet = requireNotNull(uttrekkRepository.hent(id))
            assertThat(hentet.lagretSøkId).isNull()
            assertThat(hentet.område).isEqualTo(Områder.K9)
            assertThat(hentet.harTilgangTilKode6).isEqualTo(kode6)
            assertThat(hentet.query).isEqualTo(testQuery)
            val queryService = mockk<OppgaveQueryService>()
            every { queryService.query(any(), any()) } returns emptyList()
            UttrekkJobb(queryService, tjeneste).kjørUttrekk(id)
            verify(exactly = 1) { queryService.query(match { it.harTilgangTilKode6 == kode6 && it.område == Områder.K9 }, any()) }
            assertThat(tjeneste.hentResultat(id, saksbehandlerId, Områder.K9, kode6)).isEqualTo("[]")
            assertThrows<SecurityException> { tjeneste.hentResultat(id, saksbehandlerId, Områder.K9, !kode6) }
            assertThat(tjeneste.hentForSaksbehandler(saksbehandlerId, Områder.K9, kode6)).hasSize(1)
            tjeneste.krevTilgang(id, saksbehandlerId, Områder.K9, kode6)
            tjeneste.slett(id)
            assertThat(uttrekkRepository.hent(id)).isNull()
        }
    }

    @Test
    fun `gamle K9 uttrekk uten kilde kan administreres men ukjent beskyttelse kan ikke lastes ned eller kjøres`() {
        val tjeneste = UttrekkTjeneste(uttrekkRepository, lagretSøkRepository)
        val ids = (1..2).map {
            val id = tjeneste.opprett(OpprettUttrekk(requireNotNull(testLagretSøk.id), "Test", null, null), saksbehandlerId, Områder.K9, false)
            // Faktisk gammel JSON-form: kun OppgaveQuery, uten tilgangsmetadata.
            uttrekkRepository.transactionalManager.transaction { tx ->
                tx.run(queryOf("UPDATE uttrekk SET query = :query::jsonb WHERE id = :id",
                    mapOf("query" to LosObjectMapper.instance.writeValueAsString(testQuery), "id" to id)).asUpdate)
            }
            id
        }
        tjeneste.startUttrekk(ids.first())
        tjeneste.fullførUttrekk(ids.first(), emptyList())
        uttrekkRepository.transactionalManager.transactionContext {
            lagretSøkRepository.slett(testLagretSøk)
        }
        val queryService = mockk<OppgaveQueryService>()
        UttrekkJobb(queryService, tjeneste).kjørUttrekk(ids.last())
        assertThat(uttrekkRepository.hent(ids.last())!!.status).isEqualTo(UttrekkStatus.FEILET)
        verify { queryService wasNot Called }
        for (kode6 in listOf(false, true)) {
            assertThat(tjeneste.hentForSaksbehandler(saksbehandlerId, Områder.K9, kode6)).hasSize(2)
            for (id in ids) {
                val uttrekk = tjeneste.krevTilgang(id, saksbehandlerId, Områder.K9, kode6)
                assertThat(uttrekk.område).isEqualTo(Områder.K9)
                assertThat(uttrekk.harTilgangTilKode6).isNull()
                assertThrows<SecurityException> { tjeneste.hentResultat(id, saksbehandlerId, Områder.K9, kode6) }
            }
        }
        ids.forEach { tjeneste.slett(it) }
        assertThat(tjeneste.hentForSaksbehandler(saksbehandlerId, Områder.K9, false)).hasSize(0)
    }

    @Test
    fun `skal opprette og hente uttrekk`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
            harTilgangTilKode6 = false,
        )

        val id = uttrekkRepository.opprett(uttrekk)

        val hentetUttrekk = uttrekkRepository.hent(id)
        assertThat(hentetUttrekk).isNotNull()
        assertThat(hentetUttrekk!!.id).isEqualTo(id)
        assertThat(hentetUttrekk.lagetAv).isEqualTo(saksbehandlerId)
        assertThat(hentetUttrekk.status).isEqualTo(UttrekkStatus.OPPRETTET)
        assertThat(uttrekkRepository.hentResultat(id)).isNull()
        assertThat(hentetUttrekk.antall).isNull()
    }

    @Test
    fun `skal returnere null når uttrekk ikke finnes`() {
        val hentetUttrekk = uttrekkRepository.hent(999L)
        assertThat(hentetUttrekk).isNull()
    }

    @Test
    fun `skal oppdatere eksisterende uttrekk`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
            harTilgangTilKode6 = false,
        )

        val id = uttrekkRepository.opprett(uttrekk)
        val hentetUttrekk = uttrekkRepository.hent(id)!!

        hentetUttrekk.markerSomKjører()
        uttrekkRepository.oppdater(hentetUttrekk)

        val oppdatertUttrekk = uttrekkRepository.hent(id)!!
        assertThat(oppdatertUttrekk.status).isEqualTo(UttrekkStatus.KJØRER)

        hentetUttrekk.markerSomFullført(0)
        uttrekkRepository.oppdater(hentetUttrekk, "[]")

        val fullførtUttrekk = uttrekkRepository.hent(id)!!
        assertThat(fullførtUttrekk.status).isEqualTo(UttrekkStatus.FULLFØRT)
        assertThat(uttrekkRepository.hentResultat(id)).isEqualTo("[]")
        assertThat(fullførtUttrekk.fullførtTidspunkt).isNotNull()
        assertThat(fullførtUttrekk.antall).isEqualTo(0)
    }

    @Test
    fun `skal kaste exception ved oppdatering av ikke-eksisterende uttrekk`() {
        val uttrekk = Uttrekk.fraEksisterende(
            id = 999L,
            opprettetTidspunkt = LocalDateTime.now(),
            status = UttrekkStatus.KJØRER,
            tittel = "Test uttrekk",
            query = testQuery,
            lagetAv = saksbehandlerId,
            lagretSøkId = null,
            limit = null,
            offset = null,
            feilmelding = null,
            startetTidspunkt = LocalDateTime.now(),
            fullførtTidspunkt = null,
            antall = null
        )

        val exception = assertThrows<IllegalStateException> {
            uttrekkRepository.oppdater(uttrekk)
        }

        assertThat(exception.message).isEqualTo("Feilet ved update på uttrekk. Uttrekk med id 999 finnes ikke.")
    }

    @Test
    fun `skal slette uttrekk`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
            harTilgangTilKode6 = false,
        )

        val id = uttrekkRepository.opprett(uttrekk)
        assertThat(uttrekkRepository.hent(id)).isNotNull()

        uttrekkRepository.slett(id)

        val uttrekkEtterSletting = uttrekkRepository.hent(id)
        assertThat(uttrekkEtterSletting).isNull()
    }

    @Test
    fun `skal hente alle uttrekk`() {
        val uttrekk1 = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
            harTilgangTilKode6 = false,
        )
        val uttrekk2 = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
            harTilgangTilKode6 = false,
        )

        uttrekkRepository.opprett(uttrekk1)
        uttrekkRepository.opprett(uttrekk2)

        val alleUttrekk = uttrekkRepository.hentAlle()
        assertThat(alleUttrekk.size >= 2).isEqualTo(true)
    }

    @Test
    fun `skal hente uttrekk for saksbehandler`() {
        // Opprett en annen saksbehandler for å teste filtreringen
        val annenSaksbehandlerId = runBlocking {
            testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(navident = "test2", navn = "Test Testersen 2", epost = "test2@nav.no", enhet = null),
                Områder.K9,
                skjermet = false,
            )
            saksbehandlerRepository.finnSaksbehandlerMedEpost("test2@nav.no", skjermet = false)!!.id
        }

        val uttrekk1 = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
            harTilgangTilKode6 = false,
        )
        val uttrekk2 = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
            harTilgangTilKode6 = false,
        )
        val uttrekk3 = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = annenSaksbehandlerId,
            harTilgangTilKode6 = false,
        )

        uttrekkRepository.opprett(uttrekk1)
        uttrekkRepository.opprett(uttrekk2)
        uttrekkRepository.opprett(uttrekk3)

        val uttrekkForSaksbehandler = uttrekkRepository.hentForSaksbehandler(saksbehandlerId)
        assertThat(uttrekkForSaksbehandler).hasSize(2)
        assertThat(uttrekkForSaksbehandler.all { it.lagetAv == saksbehandlerId }).isEqualTo(true)
    }

    @Test
    fun `skal opprette uttrekk`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
            harTilgangTilKode6 = false,
        )

        val id = uttrekkRepository.opprett(uttrekk)
        val hentetUttrekk = uttrekkRepository.hent(id)

        assertThat(hentetUttrekk).isNotNull()
    }

    @Test
    fun `skal sette feilmelding når uttrekk feiler`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
            harTilgangTilKode6 = false,
        )

        val id = uttrekkRepository.opprett(uttrekk)
        val hentetUttrekk = uttrekkRepository.hent(id)!!

        hentetUttrekk.markerSomKjører()
        uttrekkRepository.oppdater(hentetUttrekk)

        hentetUttrekk.markerSomFeilet("Database connection timeout")
        uttrekkRepository.oppdater(hentetUttrekk)

        val feiletUttrekk = uttrekkRepository.hent(id)!!
        assertThat(feiletUttrekk.status).isEqualTo(UttrekkStatus.FEILET)
        assertThat(feiletUttrekk.feilmelding).isEqualTo("Database connection timeout")
        assertThat(uttrekkRepository.hentResultat(id)).isNull()
        assertThat(feiletUttrekk.fullførtTidspunkt).isNotNull()
    }
}
