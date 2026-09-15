package no.nav.k9.los.lagretsok

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.oppgaveuthenting.query.dto.query.OppgaveQuery
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler

class LagretSøkRepositoryTest : AbstractK9LosIntegrationTest() {

    private lateinit var lagretSøkRepository: LagretSøkRepository
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository
    private lateinit var testSaksbehandlerRepository: TestSaksbehandlerRepository
    private lateinit var saksbehandler: Saksbehandler

    @BeforeEach
    fun setup() {
        lagretSøkRepository = get()
        saksbehandlerRepository = get()
        testSaksbehandlerRepository = get()

        runBlocking {
            saksbehandler = testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = "test",
                    navn = "Test Testersen",
                    epost = "test@nav.no",
                    enhet = null,
                )
            )
        }
    }

    @Test
    fun `skal opprette og hente lagret søk`() {
        val opprettLagretSøk = NyttLagretSøkRequest(
            tittel = "Test søk",
            query = LagretSøk.defaultQuery(Områder.K9, false)
        )

        val lagretSøk = LagretSøk.nyttSøk(Områder.K9, saksbehandler, opprettLagretSøk)
        val id = lagretSøkRepository.opprett(lagretSøk)

        val hentetSøk = lagretSøkRepository.hent(Områder.K9, saksbehandler.navident!!, id)
        assertThat(hentetSøk).isNotNull()
        assertThat(hentetSøk!!.id).isEqualTo(id)
        assertThat(hentetSøk.tittel).isEqualTo("Test søk")
        assertThat(hentetSøk.beskrivelse).isEqualTo("")
        assertThat(hentetSøk.lagetAv).isEqualTo(saksbehandler.id)
        assertThat(hentetSøk.versjon).isEqualTo(1)
    }

    @Test
    fun `skal returnere null når søk ikke finnes`() {
        val hentetSøk = lagretSøkRepository.hent(Områder.K9, saksbehandler.navident!!, 999L)
        assertThat(hentetSøk).isNull()
    }

    @Test
    fun `skal endre eksisterende lagret søk`() {
        val opprettLagretSøk = NyttLagretSøkRequest(
            tittel = "Opprinnelig tittel",
            query = LagretSøk.defaultQuery(Områder.K9, false)
        )

        val lagretSøk = LagretSøk.nyttSøk(Områder.K9, saksbehandler, opprettLagretSøk)
        val id = lagretSøkRepository.opprett(lagretSøk)

        val hentetSøk = lagretSøkRepository.hent(Områder.K9, saksbehandler.navident!!, id)!!
        val endreLagretSøk = EndreLagretSøkRequest(
            id = id,
            tittel = "Endret tittel",
            beskrivelse = "Endret beskrivelse",
            query = OppgaveQuery(),
            versjon = lagretSøk.versjon
        )

        hentetSøk.endre(endreLagretSøk, saksbehandler)
        lagretSøkRepository.endre(hentetSøk)

        val endretSøk = lagretSøkRepository.hent(Områder.K9, saksbehandler.navident!!, id)!!
        assertThat(endretSøk.tittel).isEqualTo("Endret tittel")
        assertThat(endretSøk.beskrivelse).isEqualTo("Endret beskrivelse")
        assertThat(endretSøk.versjon).isEqualTo(2)
    }

    @Test
    fun `skal slette lagret søk`() {
        val opprettLagretSøk = NyttLagretSøkRequest(
            tittel = "Søk som skal slettes",
            query = LagretSøk.defaultQuery(Områder.K9, false)
        )

        val lagretSøk = LagretSøk.nyttSøk(Områder.K9, saksbehandler, opprettLagretSøk)
        val id = lagretSøkRepository.opprett(lagretSøk)

        val hentetSøk = lagretSøkRepository.hent(Områder.K9, saksbehandler.navident!!, id)!!
        lagretSøkRepository.slett(hentetSøk)

        val søkEtterSletting = lagretSøkRepository.hent(Områder.K9, saksbehandler.navident!!, id)
        assertThat(søkEtterSletting).isNull()
    }

    @Test
    fun `skal hente alle lagrede søk for en saksbehandler`() {
        val søk1 = LagretSøk.nyttSøk(Områder.K9, saksbehandler, NyttLagretSøkRequest("Søk 1", LagretSøk.defaultQuery(Områder.K9, false)))
        val søk2 = LagretSøk.nyttSøk(Områder.K9, saksbehandler, NyttLagretSøkRequest("Søk 2", LagretSøk.defaultQuery(Områder.K9, false)))

        lagretSøkRepository.opprett(søk1)
        lagretSøkRepository.opprett(søk2)

        val alleSøk = lagretSøkRepository.hentAlle(Områder.K9, saksbehandler)
        assertThat(alleSøk).hasSize(2)
        assertThat(alleSøk.map { it.tittel }).isEqualTo(listOf("Søk 2", "Søk 1"))
    }

    @Test
    fun `skal kun hente søk som tilhører saksbehandleren`() {
        runBlocking {
            // Opprett en annen saksbehandler
            val annenSaksbehandler = testSaksbehandlerRepository.opprettSaksbehandler(
                OpprettSaksbehandler(
                    navident = "annen",
                    navn = "Annen Testersen",
                    epost = "annen@nav.no",
                    enhet = null,
                )
            )

            // Opprett søk for begge saksbehandlere
            val søkForFørsteSaksbehandler = LagretSøk.nyttSøk(Områder.K9, saksbehandler, NyttLagretSøkRequest("Søk for første", LagretSøk.defaultQuery(Områder.K9, false)))
            val søkForAnnenSaksbehandler = LagretSøk.nyttSøk(Områder.K9, annenSaksbehandler, NyttLagretSøkRequest("Søk for annen", LagretSøk.defaultQuery(Områder.K9, false)))

            lagretSøkRepository.opprett(søkForFørsteSaksbehandler)
            lagretSøkRepository.opprett(søkForAnnenSaksbehandler)

            // Hent søk for første saksbehandler - skal kun få ett resultat
            val søkForFørste = lagretSøkRepository.hentAlle(Områder.K9, saksbehandler)
            assertThat(søkForFørste).hasSize(1)
            assertThat(søkForFørste[0].tittel).isEqualTo("Søk for første")

            // Hent søk for annen saksbehandler - skal kun få ett resultat
            val søkForAnnen = lagretSøkRepository.hentAlle(Områder.K9, annenSaksbehandler)
            assertThat(søkForAnnen).hasSize(1)
            assertThat(søkForAnnen[0].tittel).isEqualTo("Søk for annen")
        }
    }

    @Test
    fun `skal kaste exception ved optimistisk låsing ved samtidig endring`() {
        val opprettLagretSøk = NyttLagretSøkRequest(
            tittel = "Test søk",
            query = LagretSøk.defaultQuery(Områder.K9, false)
        )

        val lagretSøk = LagretSøk.nyttSøk(Områder.K9, saksbehandler, opprettLagretSøk)
        val id = lagretSøkRepository.opprett(lagretSøk)

        // Simuler samtidig endring - hent to instanser av samme søk
        val førsteSøk = lagretSøkRepository.hent(Områder.K9, saksbehandler.navident!!, id)!!
        val andreSøk = lagretSøkRepository.hent(Områder.K9, saksbehandler.navident!!, id)!!

        førsteSøk.endre(
            EndreLagretSøkRequest(
                id = id,
                tittel = "Første endring",
                beskrivelse = "Første beskrivelse",
                query = OppgaveQuery(),
                versjon = førsteSøk.versjon
            ), saksbehandler
        )
        lagretSøkRepository.endre(førsteSøk)

        andreSøk.endre(
            EndreLagretSøkRequest(
                id = id,
                tittel = "Andre endring",
                beskrivelse = "Andre beskrivelse",
                query = OppgaveQuery(),
                versjon = andreSøk.versjon // Samme versjon som før første endring
            ), saksbehandler
        )

        // Dette skal feile på database-nivå med optimistisk låsing
        val exception = assertThrows<IllegalStateException> {
            lagretSøkRepository.endre(andreSøk)
        }

        assertThat(exception.message).isEqualTo("Feilet ved update på lagret søk. Kan enten skyldes at søket er slettet, eller at versjonsnummer ikke stemmer (optimistisk lås).")
    }
}