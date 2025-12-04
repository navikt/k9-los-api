package no.nav.k9.los.nyoppgavestyring.uttrekk

import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøk
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkRepository
import no.nav.k9.los.nyoppgavestyring.lagretsok.OpprettLagretSøk
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get

class UttrekkRepositoryTest : AbstractK9LosIntegrationTest() {

    private lateinit var uttrekkRepository: UttrekkRepository
    private lateinit var lagretSøkRepository: LagretSøkRepository
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository
    private var lagretSøkId: Long = 0L

    @BeforeEach
    fun setup() {
        uttrekkRepository = get()
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
            val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost("test@nav.no")!!
            val lagretSøk = LagretSøk.opprettSøk(
                OpprettLagretSøk(tittel = "Test søk"),
                saksbehandler,
                false
            )
            lagretSøkId = lagretSøkRepository.opprett(lagretSøk)
        }
    }

    @Test
    fun `skal opprette og hente uttrekk`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSokId = lagretSøkId,
            kjoreplan = "0 0 8 * * ?"
        )

        val id = uttrekkRepository.opprett(uttrekk)

        val hentetUttrekk = uttrekkRepository.hent(id)
        assertThat(hentetUttrekk).isNotNull()
        assertThat(hentetUttrekk!!.id).isEqualTo(id)
        assertThat(hentetUttrekk.lagretSøkId).isEqualTo(lagretSøkId)
        assertThat(hentetUttrekk.kjøreplan).isEqualTo("0 0 8 * * ?")
        assertThat(hentetUttrekk.status).isEqualTo(UttrekkStatus.OPPRETTET)
        assertThat(hentetUttrekk.resultat).isNull()
    }

    @Test
    fun `skal returnere null når uttrekk ikke finnes`() {
        val hentetUttrekk = uttrekkRepository.hent(999L)
        assertThat(hentetUttrekk).isNull()
    }

    @Test
    fun `skal oppdatere eksisterende uttrekk`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSokId = lagretSøkId,
            kjoreplan = null
        )

        val id = uttrekkRepository.opprett(uttrekk)
        val hentetUttrekk = uttrekkRepository.hent(id)!!

        hentetUttrekk.markerSomKjører()
        uttrekkRepository.oppdater(hentetUttrekk)

        val oppdatertUttrekk = uttrekkRepository.hent(id)!!
        assertThat(oppdatertUttrekk.status).isEqualTo(UttrekkStatus.KJØRER)

        hentetUttrekk.markerSomFullført("{\"antall\": 42}")
        uttrekkRepository.oppdater(hentetUttrekk)

        val fullførtUttrekk = uttrekkRepository.hent(id)!!
        assertThat(fullførtUttrekk.status).isEqualTo(UttrekkStatus.FULLFØRT)
        assertThat(fullførtUttrekk.resultat).isEqualTo("{\"antall\": 42}")
        assertThat(fullførtUttrekk.fullførtTidspunkt).isNotNull()
    }

    @Test
    fun `skal kaste exception ved oppdatering av ikke-eksisterende uttrekk`() {
        val uttrekk = Uttrekk.fraEksisterende(
            id = 999L,
            opprettetTidspunkt = java.time.LocalDateTime.now(),
            status = UttrekkStatus.KJØRER,
            lagretSokId = lagretSøkId,
            kjoreplan = null,
            typeKjoring = TypeKjøring.OPPGAVER,
            resultat = null,
            feilmelding = null,
            startetTidspunkt = java.time.LocalDateTime.now(),
            fullførtTidspunkt = null
        )

        val exception = assertThrows<IllegalStateException> {
            uttrekkRepository.oppdater(uttrekk)
        }

        assertThat(exception.message).isEqualTo("Feilet ved update på uttrekk. Uttrekk med id 999 finnes ikke.")
    }

    @Test
    fun `skal slette uttrekk`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSokId = lagretSøkId,
            kjoreplan = null
        )

        val id = uttrekkRepository.opprett(uttrekk)
        assertThat(uttrekkRepository.hent(id)).isNotNull()

        uttrekkRepository.slett(id)

        val uttrekkEtterSletting = uttrekkRepository.hent(id)
        assertThat(uttrekkEtterSletting).isNull()
    }

    @Test
    fun `skal hente alle uttrekk`() {
        val uttrekk1 = Uttrekk.opprettUttrekk(lagretSokId = lagretSøkId, kjoreplan = null)
        val uttrekk2 = Uttrekk.opprettUttrekk(lagretSokId = lagretSøkId, kjoreplan = "0 0 8 * * ?")

        uttrekkRepository.opprett(uttrekk1)
        uttrekkRepository.opprett(uttrekk2)

        val alleUttrekk = uttrekkRepository.hentAlle()
        assertThat(alleUttrekk.size >= 2).isEqualTo(true)
    }

    @Test
    fun `skal hente uttrekk for spesifikt lagret søk`() {
        // Opprett et ekstra lagret søk for å teste filtreringen
        val saksbehandler = runBlocking {
            saksbehandlerRepository.finnSaksbehandlerMedEpost("test@nav.no")!!
        }
        val annetLagretSøk = LagretSøk.opprettSøk(
            OpprettLagretSøk(tittel = "Annet søk"),
            saksbehandler,
            false
        )
        val annetLagretSøkId = lagretSøkRepository.opprett(annetLagretSøk)

        val uttrekk1 = Uttrekk.opprettUttrekk(lagretSokId = lagretSøkId, kjoreplan = null)
        val uttrekk2 = Uttrekk.opprettUttrekk(lagretSokId = lagretSøkId, kjoreplan = "0 0 8 * * ?")
        val uttrekk3 = Uttrekk.opprettUttrekk(lagretSokId = annetLagretSøkId, kjoreplan = null)

        uttrekkRepository.opprett(uttrekk1)
        uttrekkRepository.opprett(uttrekk2)
        uttrekkRepository.opprett(uttrekk3)

        val uttrekkForLagretSok = uttrekkRepository.hentForLagretSok(lagretSøkId)
        assertThat(uttrekkForLagretSok).hasSize(2)
        assertThat(uttrekkForLagretSok.all { it.lagretSøkId == lagretSøkId }).isEqualTo(true)
    }
}
