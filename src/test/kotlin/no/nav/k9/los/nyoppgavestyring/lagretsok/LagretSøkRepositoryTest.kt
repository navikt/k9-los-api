package no.nav.k9.los.nyoppgavestyring.lagretsok

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get

class LagretSøkRepositoryTest : AbstractK9LosIntegrationTest() {

    private lateinit var lagretSøkRepository: LagretSøkRepository
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository
    private lateinit var saksbehandler: Saksbehandler

    @BeforeEach
    fun setup() {
        lagretSøkRepository = get()
        saksbehandlerRepository = get()

        runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    brukerIdent = "test",
                    navn = "Test Testersen",
                    epost = "test@nav.no",
                    reservasjoner = mutableSetOf(),
                    enhet = null,
                )
            )
            saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost("test@nav.no")!!
        }
    }

    @Test
    fun `skal opprette og hente lagret søk`() {
        val opprettLagretSøk = OpprettLagretSøk(
            tittel = "Test søk"
        )

        val lagretSøk = LagretSøk.opprettSøk(opprettLagretSøk, saksbehandler)
        val id = lagretSøkRepository.opprett(lagretSøk)

        val hentetSøk = lagretSøkRepository.hent(id)
        assertThat(hentetSøk).isNotNull()
        assertThat(hentetSøk!!.id).isEqualTo(id)
        assertThat(hentetSøk.tittel).isEqualTo("Test søk")
        assertThat(hentetSøk.beskrivelse).isEqualTo("")
        assertThat(hentetSøk.lagetAv).isEqualTo(saksbehandler.id)
        assertThat(hentetSøk.versjon).isEqualTo(1)
    }

    @Test
    fun `skal returnere null når søk ikke finnes`() {
        val hentetSøk = lagretSøkRepository.hent(999L)
        assertThat(hentetSøk).isNull()
    }

    @Test
    fun `skal endre eksisterende lagret søk`() {
        val opprettLagretSøk = OpprettLagretSøk(
            tittel = "Opprinnelig tittel"
        )

        val lagretSøk = LagretSøk.opprettSøk(opprettLagretSøk, saksbehandler)
        val id = lagretSøkRepository.opprett(lagretSøk)

        val hentetSøk = lagretSøkRepository.hent(id)!!
        val endreLagretSøk = EndreLagretSøk(
            id = id,
            tittel = "Endret tittel",
            beskrivelse = "Endret beskrivelse",
            query = OppgaveQuery(),
            versjon = lagretSøk.versjon
        )

        hentetSøk.endre(endreLagretSøk, saksbehandler)
        lagretSøkRepository.endre(hentetSøk)

        val endretSøk = lagretSøkRepository.hent(id)!!
        assertThat(endretSøk.tittel).isEqualTo("Endret tittel")
        assertThat(endretSøk.beskrivelse).isEqualTo("Endret beskrivelse")
        assertThat(endretSøk.versjon).isEqualTo(2)
    }

    @Test
    fun `skal slette lagret søk`() {
        val opprettLagretSøk = OpprettLagretSøk(
            tittel = "Søk som skal slettes"
        )

        val lagretSøk = LagretSøk.opprettSøk(opprettLagretSøk, saksbehandler)
        val id = lagretSøkRepository.opprett(lagretSøk)

        val hentetSøk = lagretSøkRepository.hent(id)!!
        lagretSøkRepository.slett(hentetSøk)

        val søkEtterSletting = lagretSøkRepository.hent(id)
        assertThat(søkEtterSletting).isNull()
    }

    @Test
    fun `skal hente alle lagrede søk for en saksbehandler`() {
        val søk1 = LagretSøk.opprettSøk(
            OpprettLagretSøk("Søk 1"),
            saksbehandler
        )
        val søk2 = LagretSøk.opprettSøk(
            OpprettLagretSøk("Søk 2"),
            saksbehandler
        )

        lagretSøkRepository.opprett(søk1)
        lagretSøkRepository.opprett(søk2)

        val alleSøk = lagretSøkRepository.hentAlle(saksbehandler)
        assertThat(alleSøk).hasSize(2)
        assertThat(alleSøk.map { it.tittel }).isEqualTo(listOf("Søk 1", "Søk 2"))
    }

    @Test
    fun `skal kun hente søk som tilhører saksbehandleren`() {
        runBlocking {
            // Opprett en annen saksbehandler
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    brukerIdent = "annen",
                    navn = "Annen Testersen",
                    epost = "annen@nav.no",
                    reservasjoner = mutableSetOf(),
                    enhet = null,
                )
            )
            val annenSaksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost("annen@nav.no")!!

            // Opprett søk for begge saksbehandlere
            val søkForFørsteSaksbehandler = LagretSøk.opprettSøk(
                OpprettLagretSøk("Søk for første"),
                saksbehandler
            )
            val søkForAnnenSaksbehandler = LagretSøk.opprettSøk(
                OpprettLagretSøk("Søk for annen"),
                annenSaksbehandler
            )

            lagretSøkRepository.opprett(søkForFørsteSaksbehandler)
            lagretSøkRepository.opprett(søkForAnnenSaksbehandler)

            // Hent søk for første saksbehandler - skal kun få ett resultat
            val søkForFørste = lagretSøkRepository.hentAlle(saksbehandler)
            assertThat(søkForFørste).hasSize(1)
            assertThat(søkForFørste[0].tittel).isEqualTo("Søk for første")

            // Hent søk for annen saksbehandler - skal kun få ett resultat
            val søkForAnnen = lagretSøkRepository.hentAlle(annenSaksbehandler)
            assertThat(søkForAnnen).hasSize(1)
            assertThat(søkForAnnen[0].tittel).isEqualTo("Søk for annen")
        }
    }
}