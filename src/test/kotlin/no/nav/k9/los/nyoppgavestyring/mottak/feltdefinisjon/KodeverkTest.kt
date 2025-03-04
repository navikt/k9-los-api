package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import assertk.assertThat
import assertk.assertions.isEqualTo
import junit.framework.TestCase.assertNotNull
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.db.util.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get

class KodeverkTest : AbstractK9LosIntegrationTest() {
    private lateinit var område: Område
    private lateinit var feltdefinisjonRepository: FeltdefinisjonRepository
    private lateinit var transactionalManager: TransactionalManager
    private lateinit var områdeRepository: OmrådeRepository

    private val testFeltdefinissjonEksternId = "testFeltdefinisjonEksternId"

    @BeforeEach
    fun setup() {
        feltdefinisjonRepository = get()
        transactionalManager = get()
        områdeRepository = get()

        områdeRepository.lagre(eksternId = "K9")

        område = områdeRepository.hent("K9")!!
    }

    @Test
    fun `lagreOgHentKodeverkTest`() {
        val kodeverk = byggKodeverk()
        transactionalManager.transaction { tx ->
            feltdefinisjonRepository.lagre(kodeverk, tx)

            val kodeverkForOmråde = feltdefinisjonRepository.hentKodeverk(område, tx)

            val kodeverk = kodeverkForOmråde.hentKodeverk("kodeverk")

            val kodeverkVerdi = kodeverk.hentVerdi("verdi1")

            assertNotNull(kodeverkVerdi)
            assertThat(kodeverkVerdi!!.beskrivelse).isEqualTo("beskrivelse1")
        }
    }

    @Test
    fun `lagreOgHenteFeltdefinisjonMedKodeverk`() {
        var kodeverk = byggKodeverk()

        transactionalManager.transaction { tx ->
            feltdefinisjonRepository.lagre(kodeverk, tx)
            kodeverk = feltdefinisjonRepository.hentKodeverk(område, tx).hentKodeverk("kodeverk")
            val feltdefinisjon = byggFeltdefinisjon(område, Kodeverkreferanse(kodeverk))

            feltdefinisjonRepository.leggTil(
                leggTilListe = setOf(feltdefinisjon),
                område = område,
                tx = tx
            )

            val feltdefinisjonHentet = feltdefinisjonRepository.hent(område, tx).hentFeltdefinisjon(testFeltdefinissjonEksternId)

            assertThat(feltdefinisjonHentet.eksternId).isEqualTo(testFeltdefinissjonEksternId)
            assertThat(feltdefinisjonHentet.kodeverkreferanse).isEqualTo(Kodeverkreferanse(kodeverk))
        }
    }

    internal fun byggFeltdefinisjon(område: Område, kodeverkreferanse: Kodeverkreferanse?): Feltdefinisjon {
         return Feltdefinisjon(
             id = null,
             eksternId = testFeltdefinissjonEksternId,
             visningsnavn = "Test",
             område = område,
             listetype = false,
             tolkesSom = "String",
             visTilBruker = true,
             kokriterie = false,
             kodeverkreferanse = kodeverkreferanse,
             transientFeltutleder = null,
         )
    }

    internal fun byggKodeverk(): Kodeverk {
        return Kodeverk(
            id = null,
            område = område,
            eksternId = "kodeverk",
            beskrivelse = null,
            uttømmende = false,
            verdier = listOf(
                Kodeverkverdi(
                    id = null,
                    verdi = "verdi1",
                    visningsnavn = "navn1",
                    beskrivelse = "beskrivelse1",
                    favoritt = false
                ),
                Kodeverkverdi(
                    id = null,
                    verdi = "verdi2",
                    visningsnavn = "navn2",
                    beskrivelse = "beskrivelse2",
                    favoritt = false
                )
            )
        )
    }
}