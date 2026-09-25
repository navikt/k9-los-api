package no.nav.k9.los.uttrekk

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.lagretsok.LagretSøk
import no.nav.k9.los.lagretsok.LagretSøkRepository
import no.nav.k9.los.lagretsok.NyttLagretSøkRequest
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import java.time.LocalDateTime
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler

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
            val saksbehandler = testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = "test",
                    navn = "Test Testersen",
                    epost = "test@nav.no",
                    enhet = null,
                    områder = listOf(Områder.K9),
                )
            )
            saksbehandlerId = saksbehandler.id
            val lagretSøk = LagretSøk.nyttSøk(
                Områder.K9,
                saksbehandler,
                NyttLagretSøkRequest(tittel = "Test søk", query = LagretSøk.defaultQuery(Områder.K9, false))
            )
            lagretSøkRepository.opprett(lagretSøk)
            testQuery = lagretSøk.query
            testLagretSøk = lagretSøk
        }
    }

    @Test
    fun `skal opprette og hente uttrekk`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
        )

        val id = uttrekkRepository.opprett(uttrekk)

        val hentetUttrekk = uttrekkRepository.hent(Områder.K9, "test", id)
        assertThat(hentetUttrekk).isNotNull()
        assertThat(hentetUttrekk!!.id).isEqualTo(id)
        assertThat(hentetUttrekk.lagetAv).isEqualTo(saksbehandlerId)
        assertThat(hentetUttrekk.status).isEqualTo(UttrekkStatus.OPPRETTET)
        assertThat(uttrekkRepository.hentResultat(Områder.K9, "test", id)).isNull()
        assertThat(hentetUttrekk.antall).isNull()
    }

    @Test
    fun `skal returnere null når uttrekk ikke finnes`() {
        val hentetUttrekk = uttrekkRepository.hent(Områder.K9, "test", 999L)
        assertThat(hentetUttrekk).isNull()
    }

    @Test
    fun `skal oppdatere eksisterende uttrekk`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
        )

        val id = uttrekkRepository.opprett(uttrekk)
        val hentetUttrekk = uttrekkRepository.hent(Områder.K9, "test", id)!!

        hentetUttrekk.markerSomKjører()
        uttrekkRepository.oppdater(hentetUttrekk)

        val oppdatertUttrekk = uttrekkRepository.hent(Områder.K9, "test", id)!!
        assertThat(oppdatertUttrekk.status).isEqualTo(UttrekkStatus.KJØRER)

        hentetUttrekk.markerSomFullført(0)
        uttrekkRepository.oppdater(hentetUttrekk, "[]")

        val fullførtUttrekk = uttrekkRepository.hent(Områder.K9, "test", id)!!
        assertThat(fullførtUttrekk.status).isEqualTo(UttrekkStatus.FULLFØRT)
        assertThat(uttrekkRepository.hentResultat(Områder.K9, "test", id)).isEqualTo("[]")
        assertThat(fullførtUttrekk.fullførtTidspunkt).isNotNull()
        assertThat(fullførtUttrekk.antall).isEqualTo(0)
    }

    @Test
    fun `skal kaste exception ved oppdatering av ikke-eksisterende uttrekk`() {
        val uttrekk = Uttrekk.fraEksisterende(
            id = 999L,
            område = Områder.K9,
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
        )

        val id = uttrekkRepository.opprett(uttrekk)
        assertThat(uttrekkRepository.hent(Områder.K9, "test", id)).isNotNull()

        uttrekkRepository.slett(uttrekkRepository.hentForJobb(id)!!)

        val uttrekkEtterSletting = uttrekkRepository.hent(Områder.K9, "test", id)
        assertThat(uttrekkEtterSletting).isNull()
    }

    @Test
    fun `skal hente alle uttrekk`() {
        val uttrekk1 = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
        )
        val uttrekk2 = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
        )

        uttrekkRepository.opprett(uttrekk1)
        uttrekkRepository.opprett(uttrekk2)

        val alleUttrekk = uttrekkRepository.hentAlleForJobb()
        assertThat(alleUttrekk.size >= 2).isEqualTo(true)
    }

    @Test
    fun `skal hente uttrekk for saksbehandler`() {
        // Opprett en annen saksbehandler for å teste filtreringen
        val annenSaksbehandlerId = testSaksbehandlerRepository.opprettSaksbehandler(
            OpprettSaksbehandler(
                navident = "test2",
                navn = "Test Testersen 2",
                epost = "test2@nav.no",
                enhet = null,
                områder = listOf(Områder.K9),
            )
        ).id

        val uttrekk1 = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
        )
        val uttrekk2 = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
        )
        val uttrekk3 = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = annenSaksbehandlerId,
        )

        uttrekkRepository.opprett(uttrekk1)
        uttrekkRepository.opprett(uttrekk2)
        uttrekkRepository.opprett(uttrekk3)

        val uttrekkForSaksbehandler = uttrekkRepository.hentForSaksbehandler(Områder.K9, saksbehandlerId)
        assertThat(uttrekkForSaksbehandler).hasSize(2)
        assertThat(uttrekkForSaksbehandler.all { it.lagetAv == saksbehandlerId }).isEqualTo(true)
    }

    @Test
    fun `skal opprette uttrekk`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
        )

        val id = uttrekkRepository.opprett(uttrekk)
        val hentetUttrekk = uttrekkRepository.hent(Områder.K9, "test", id)

        assertThat(hentetUttrekk).isNotNull()
    }

    @Test
    fun `skal sette feilmelding når uttrekk feiler`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
        )

        val id = uttrekkRepository.opprett(uttrekk)
        val hentetUttrekk = uttrekkRepository.hent(Områder.K9, "test", id)!!

        hentetUttrekk.markerSomKjører()
        uttrekkRepository.oppdater(hentetUttrekk)

        hentetUttrekk.markerSomFeilet("Database connection timeout")
        uttrekkRepository.oppdater(hentetUttrekk)

        val feiletUttrekk = uttrekkRepository.hent(Områder.K9, "test", id)!!
        assertThat(feiletUttrekk.status).isEqualTo(UttrekkStatus.FEILET)
        assertThat(feiletUttrekk.feilmelding).isEqualTo("Database connection timeout")
        assertThat(uttrekkRepository.hentResultat(Områder.K9, "test", id)).isNull()
        assertThat(feiletUttrekk.fullførtTidspunkt).isNotNull()
    }

    @Test
    fun `slettForLagretSøk sletter kun når lagret søk tilhører innlogget bruker i området`() {
        testSaksbehandlerRepository.opprettSaksbehandler(
            OpprettSaksbehandler(
                navident = "annen",
                navn = "Annen Bruker",
                epost = "annen@nav.no",
                enhet = null,
                områder = listOf(Områder.K9),
            )
        )
        val lagretSøkId = lagretSøkRepository.opprett(testLagretSøk)
        val lagretSøk = lagretSøkRepository.hent(Områder.K9, "test", lagretSøkId)!!
        val uttrekkId = uttrekkRepository.opprett(Uttrekk.opprettUttrekk(lagretSøk = lagretSøk, lagetAv = saksbehandlerId))
        val uttrekkTjeneste = get<UttrekkTjeneste>()

        assertThat(uttrekkTjeneste.slettForLagretSøk(Områder.K9, "annen", lagretSøkId)).isEqualTo(0)
        assertThat(uttrekkRepository.hent(Områder.K9, "test", uttrekkId)).isNotNull()

        assertThat(uttrekkTjeneste.slettForLagretSøk(Områder.K9, "test", lagretSøkId)).isEqualTo(1)
        assertThat(uttrekkRepository.hent(Områder.K9, "test", uttrekkId)).isNull()
    }
}
