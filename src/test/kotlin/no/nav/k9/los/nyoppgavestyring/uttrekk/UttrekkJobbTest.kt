package no.nav.k9.los.nyoppgavestyring.uttrekk

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.startsWith
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøk
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkRepository
import no.nav.k9.los.nyoppgavestyring.lagretsok.NyttLagretSøkRequest
import no.nav.k9.los.nyoppgavestyring.query.dto.query.EnkelSelectFelt
import no.nav.k9.los.nyoppgavestyring.query.dto.query.OppgaveQuery
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get

class UttrekkJobbTest : AbstractK9LosIntegrationTest() {

    private lateinit var uttrekkJobb: UttrekkJobb
    private lateinit var uttrekkTjeneste: UttrekkTjeneste
    private lateinit var uttrekkRepository: UttrekkRepository
    private lateinit var lagretSøkRepository: LagretSøkRepository
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository
    private var saksbehandlerId: Long = 0L
    private lateinit var testQuery: OppgaveQuery
    private lateinit var testLagretSøk: LagretSøk

    @BeforeEach
    fun setup() {
        uttrekkJobb = get()
        uttrekkTjeneste = get()
        uttrekkRepository = get()
        lagretSøkRepository = get()
        saksbehandlerRepository = get()

        runBlocking {
            saksbehandlerRepository.addSaksbehandler(
                Saksbehandler(
                    id = null,
                    navident = "test",
                    navn = "Test Testersen",
                    epost = "test@nav.no",
                    enhet = null,
                )
            )
            val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost("test@nav.no")!!
            saksbehandlerId = saksbehandler.id!!
            val lagretSøk = LagretSøk.nyttSøk(
                NyttLagretSøkRequest(tittel = "Test søk", query = OppgaveQuery(filtere = listOf(), select = listOf(EnkelSelectFelt("K9", "saksnummer")))),
                saksbehandler,
            )
            lagretSøkRepository.opprett(lagretSøk)
            testQuery = lagretSøk.query
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

        val fullførtUttrekk = uttrekkRepository.hent(uttrekkId)!!
        assertThat(fullførtUttrekk.status).isEqualTo(UttrekkStatus.FULLFØRT)
        assertThat(fullførtUttrekk.antall).isEqualTo(0)
        assertThat(uttrekkRepository.hentResultat(uttrekkId)).isEqualTo("[]")
        assertThat(fullførtUttrekk.feilmelding).isNull()
    }

    // TODO: Lag test som faktisk returnerer resultat
}
