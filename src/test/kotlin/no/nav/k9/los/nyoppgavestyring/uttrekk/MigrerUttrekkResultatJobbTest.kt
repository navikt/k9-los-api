package no.nav.k9.los.nyoppgavestyring.uttrekk

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import kotlinx.coroutines.runBlocking
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøk
import no.nav.k9.los.nyoppgavestyring.lagretsok.LagretSøkRepository
import no.nav.k9.los.nyoppgavestyring.lagretsok.NyttLagretSøkRequest
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.nyoppgavestyring.saksbehandleradmin.SaksbehandlerRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get

class MigrerUttrekkResultatJobbTest : AbstractK9LosIntegrationTest() {

    private lateinit var uttrekkRepository: UttrekkRepository
    private lateinit var lagretSøkRepository: LagretSøkRepository
    private lateinit var saksbehandlerRepository: SaksbehandlerRepository
    private lateinit var jobb: MigrerUttrekkResultatJobb
    private var saksbehandlerId: Long = 0L
    private lateinit var testLagretSøk: LagretSøk

    @BeforeEach
    fun setup() {
        uttrekkRepository = get()
        lagretSøkRepository = get()
        saksbehandlerRepository = get()
        jobb = MigrerUttrekkResultatJobb(uttrekkRepository)

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
            val lagretSøk = LagretSøk.nyttSøk(
                NyttLagretSøkRequest(tittel = "Test søk", query = LagretSøk.defaultQuery(false)),
                saksbehandler,
            )
            lagretSøkRepository.opprett(lagretSøk)
            testLagretSøk = lagretSøk
        }
    }

    @Test
    fun `skal konvertere gammelt format til nytt format`() {
        val gammeltResultat = """
            [
              {
                "id": {"område": "K9", "eksternId": "abc123"},
                "felter": [
                  {"kode": "saksnummer", "område": "K9", "verdi": "1270379"},
                  {"kode": "behandlingstype", "område": "K9", "verdi": "Pleiepenger"}
                ]
              },
              {
                "id": {"område": "K9", "eksternId": "def456"},
                "felter": [
                  {"kode": "saksnummer", "område": "K9", "verdi": "1336828"},
                  {"kode": "behandlingstype", "område": "K9", "verdi": "Omsorgspenger"}
                ]
              }
            ]
        """.trimIndent()

        val uttrekkId = opprettUttrekkMedResultat(gammeltResultat)

        jobb.kjør()

        val rader = UttrekkResultatMapper.fraLagretJson(uttrekkRepository.hentResultat(uttrekkId)!!)
        assertThat(rader.size).isEqualTo(2)
        assertThat(rader[0].id).isEqualTo("abc123")
        assertThat(rader[0].kolonner[0]).isEqualTo("1270379")
        assertThat(rader[1].id).isEqualTo("def456")
    }

    @Test
    fun `skal ikke endre uttrekk som allerede er i nytt format`() {
        val nyttResultat = """
            [
              {
                "id": "abc123",
                "kolonner": [
                  "1270379"
                ]
              }
            ]
        """.trimIndent()

        val uttrekkId = opprettUttrekkMedResultat(nyttResultat)

        jobb.kjør()

        val rader = UttrekkResultatMapper.fraLagretJson(uttrekkRepository.hentResultat(uttrekkId)!!)
        assertThat(rader.size).isEqualTo(1)
        assertThat(rader[0].id).isEqualTo("abc123")
        assertThat(rader[0].kolonner[0]).isEqualTo("1270379")
    }

    @Test
    fun `skal ikke berøre uttrekk uten resultat`() {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            typeKjoring = TypeKjøring.ANTALL,
            lagetAv = saksbehandlerId,
        )
        val uttrekkId = uttrekkRepository.opprett(uttrekk)

        jobb.kjør()

        assertThat(uttrekkRepository.hentResultat(uttrekkId)).isNull()
    }

    @Test
    fun `skal håndtere tomt resultatarray`() {
        val uttrekkId = opprettUttrekkMedResultat("[]")

        jobb.kjør()

        assertThat(uttrekkRepository.hentResultat(uttrekkId)).isNotNull()
    }

    private fun opprettUttrekkMedResultat(resultatJson: String): Long {
        val uttrekk = Uttrekk.opprettUttrekk(
            lagretSøk = testLagretSøk,
            typeKjoring = TypeKjøring.OPPGAVER,
            lagetAv = saksbehandlerId,
        )
        val uttrekkId = uttrekkRepository.opprett(uttrekk)
        val hentet = uttrekkRepository.hent(uttrekkId)!!
        hentet.markerSomKjører()
        hentet.markerSomFullført(1)
        uttrekkRepository.oppdater(hentet, resultatJson)
        return uttrekkId
    }
}
