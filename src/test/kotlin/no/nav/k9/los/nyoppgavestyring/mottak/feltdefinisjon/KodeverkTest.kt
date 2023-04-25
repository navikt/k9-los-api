package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import assertk.assertThat
import assertk.assertions.isEqualTo
import junit.framework.TestCase.assertNotNull
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domene.lager.oppgave.v2.TransactionalManager
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

    @BeforeEach
    fun setup() {
        feltdefinisjonRepository = get()
        transactionalManager = get()
        områdeRepository = get()

        if (områdeRepository.hent("K9") == null) {
            områdeRepository.lagre(eksternId = "K9")
        }

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

            val feltdefinisjonHentet = feltdefinisjonRepository.hent(område, tx).hentFeltdefinisjon("Feltdefinisjon")

            assertThat(feltdefinisjonHentet.eksternId).isEqualTo("Feltdefinisjon")
            assertThat(feltdefinisjonHentet.kodeverkreferanse).isEqualTo(Kodeverkreferanse(kodeverk))
        }
    }

    internal fun byggFeltdefinisjon(område: Område, kodeverk: Kodeverkreferanse?): Feltdefinisjon {
         return Feltdefinisjon(
             id = null,
             eksternId = "Feltdefinisjon",
             område = område,
             listetype = false,
             tolkesSom = "String",
             visTilBruker = true,
             kodeverkreferanse = kodeverk
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
                    beskrivelse = "beskrivelse1"
                ),
                Kodeverkverdi(
                    id = null,
                    verdi = "verdi2",
                    visningsnavn = "navn2",
                    beskrivelse = "beskrivelse2"
                )
            )
        )
    }
}