package no.nav.k9.los.uttrekk

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.lagretsok.LagretSøk
import no.nav.k9.los.lagretsok.LagretSøkRepository
import no.nav.k9.los.lagretsok.NyttLagretSøkRequest
import no.nav.k9.los.oppgavedefinisjon.omraade.Områder
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.saksbehandleradmin.TestSaksbehandlerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import no.nav.k9.los.saksbehandleradmin.OpprettSaksbehandler

class UttrekkJobbTest : AbstractK9LosIntegrationTest() {

    private lateinit var uttrekkJobb: UttrekkJobb
    private lateinit var uttrekkTjeneste: UttrekkTjeneste
    private lateinit var uttrekkRepository: UttrekkRepository
    private lateinit var lagretSøkRepository: LagretSøkRepository
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository
    private lateinit var testSaksbehandlerRepository: TestSaksbehandlerRepository
    private var saksbehandlerId: Long = 0L
    private lateinit var testLagretSøk: LagretSøk

    @BeforeEach
    fun setup() {
        uttrekkJobb = get()
        uttrekkTjeneste = get()
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
            testLagretSøk = lagretSøk
        }
    }

    @Test
    fun `skal kjøre uttrekk uten oppgaver og fullføre med tomt resultat`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            lagetAv = saksbehandlerId,
        )
        val uttrekkId = uttrekkRepository.opprett(uttrekk)

        uttrekkJobb.kjørUttrekk(uttrekkId)

        val fullførtUttrekk = uttrekkRepository.hentForJobb(uttrekkId)!!
        assertThat(fullførtUttrekk.status).isEqualTo(UttrekkStatus.FULLFØRT)
        assertThat(fullførtUttrekk.antall).isEqualTo(0)
        assertThat(uttrekkRepository.hentResultat(Områder.K9, "test", uttrekkId)).isEqualTo("[]")
        assertThat(fullførtUttrekk.feilmelding).isNull()
    }

    // TODO: Lag test som faktisk returnerer resultat
}
