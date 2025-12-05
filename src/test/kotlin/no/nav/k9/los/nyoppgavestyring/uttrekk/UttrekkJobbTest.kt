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
import no.nav.k9.los.nyoppgavestyring.lagretsok.OpprettLagretSøk
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
                    brukerIdent = "test",
                    navn = "Test Testersen",
                    epost = "test@nav.no",
                    reservasjoner = mutableSetOf(),
                    enhet = null,
                )
            )
            val saksbehandler = saksbehandlerRepository.finnSaksbehandlerMedEpost("test@nav.no")!!
            saksbehandlerId = saksbehandler.id!!
            val lagretSøk = LagretSøk.opprettSøk(
                OpprettLagretSøk(tittel = "Test søk"),
                saksbehandler,
                false
            )
            lagretSøkRepository.opprett(lagretSøk)
            testQuery = lagretSøk.query
        }
    }

    @Test
    fun `skal kjøre uttrekk med TypeKjøring ANTALL og returnere antall som string`() {
        // Opprett uttrekk med TypeKjøring.ANTALL
        val uttrekk = Uttrekk.opprettUttrekk(
            query = testQuery,
            typeKjoring = TypeKjøring.ANTALL,
            lagetAv = saksbehandlerId,
            timeout = 30
        )
        val uttrekkId = uttrekkRepository.opprett(uttrekk)

        // Kjør uttrekket
        uttrekkJobb.kjørUttrekk(uttrekkId)

        // Verifiser resultat
        val fullførtUttrekk = uttrekkRepository.hent(uttrekkId)!!
        assertThat(fullførtUttrekk.status).isEqualTo(UttrekkStatus.FULLFØRT)
        // For ANTALL skal antall være satt, men ikke resultat
        assertThat(fullførtUttrekk.antall).isNotNull()
        assertThat(uttrekkRepository.hentResultat(uttrekkId)).isNull()
        assertThat(fullførtUttrekk.feilmelding).isNull()
    }

    @Test
    fun `skal kjøre uttrekk med TypeKjøring OPPGAVER og returnere JSON med oppgaver`() {
        // Opprett uttrekk med TypeKjøring.OPPGAVER
        val uttrekk = Uttrekk.opprettUttrekk(
            query = testQuery,
            typeKjoring = TypeKjøring.OPPGAVER,
            lagetAv = saksbehandlerId,
            timeout = 30
        )
        val uttrekkId = uttrekkRepository.opprett(uttrekk)

        // Kjør uttrekket
        uttrekkJobb.kjørUttrekk(uttrekkId)

        // Verifiser resultat
        val fullførtUttrekk = uttrekkRepository.hent(uttrekkId)!!
        assertThat(fullførtUttrekk.status).isEqualTo(UttrekkStatus.FULLFØRT)
        val resultat = uttrekkRepository.hentResultat(uttrekkId)
        assertThat(resultat).isNotNull()
        // Resultat skal være JSON array (selv om det er tomt)
        assertThat(resultat.toString()).startsWith("[")
        assertThat(fullførtUttrekk.feilmelding).isNull()
        // AntallRader skal være satt for OPPGAVER
        assertThat(fullførtUttrekk.antall).isNotNull()
    }
}
