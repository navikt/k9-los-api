package no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.infrastruktur.db.TransactionalManager
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.OmrådeRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.koin.test.get

class FeltdefinisjonRepositoryTest: AbstractK9LosIntegrationTest() {
    private lateinit var fdRepository: FeltdefinisjonRepository
    private lateinit var områdeRepository: OmrådeRepository
    private lateinit var transactionalManager: TransactionalManager

    @BeforeEach
    fun setup() {
        transactionalManager = get()
        områdeRepository = get()
        områdeRepository.lagre("test")
        fdRepository = get()
    }
    @Test
    fun `lagre og hente feltdefinisjon`() {
            val område = områdeRepository.hent("test")!!
            val feltdefinisjon = setOf(Feltdefinisjon(
                eksternId = "test123",
                område = område,
                visningsnavn = "test123",
                listetype = false,
                tolkesSom = "string",
                visTilBruker = true,
                kokriterie = true,
                kodeverkreferanse = null,
                transientFeltutleder = null,
            ))
        transactionalManager.transaction { tx ->
            fdRepository.leggTil(feltdefinisjon, område, tx)
        }

        val lagretFeltdefinisjon = transactionalManager.transaction { tx ->
            fdRepository.hent(område, tx)
        }

        assertThat(lagretFeltdefinisjon.feltdefinisjoner.size).isEqualTo(1)
        assertThat(lagretFeltdefinisjon.feltdefinisjoner.elementAt(0).eksternId).isEqualTo("test123")
        assertThat(lagretFeltdefinisjon.feltdefinisjoner.elementAt(0).visningsnavn).isEqualTo("test123")
        assertThat(lagretFeltdefinisjon.feltdefinisjoner.elementAt(0).listetype).isEqualTo(false)

        val feltdefinisjonOppdatering = setOf(Feltdefinisjon(
            eksternId = "test123",
            område = område,
            visningsnavn = "test1234",
            listetype = true,
            tolkesSom = "string",
            visTilBruker = false,
            kokriterie = false,
            kodeverkreferanse = null,
            transientFeltutleder = null,
        ))

        transactionalManager.transaction { tx ->
            fdRepository.oppdater(feltdefinisjonOppdatering, område, tx)
        }

        val oppdatertFeltdefinisjon = transactionalManager.transaction { tx ->
            fdRepository.hent(område, tx)
        }

        assertThat(oppdatertFeltdefinisjon.feltdefinisjoner.size).isEqualTo(1)
        assertThat(oppdatertFeltdefinisjon.feltdefinisjoner.elementAt(0).eksternId).isEqualTo("test123")
        assertThat(oppdatertFeltdefinisjon.feltdefinisjoner.elementAt(0).visningsnavn).isEqualTo("test1234")
        assertThat(oppdatertFeltdefinisjon.feltdefinisjoner.elementAt(0).listetype).isEqualTo(true)

        assertThrows<IllegalArgumentException> {
            transactionalManager.transaction { tx ->
                fdRepository.fjern(feltdefinisjonOppdatering, tx)
            }
        }

        transactionalManager.transaction { tx ->
            fdRepository.fjern(oppdatertFeltdefinisjon.feltdefinisjoner, tx)
        }

        val slettetFeltdefinisjon = transactionalManager.transaction { tx ->
            fdRepository.hent(område, tx)
        }

        assertThat(slettetFeltdefinisjon.feltdefinisjoner).isEqualTo(emptySet<Feltdefinisjon>())
    }

    @Test
    fun `crud kodeverk`() {
        val område = områdeRepository.hent("test")!!
        val kodeverk = Kodeverk(
            område = område,
            eksternId = "testkodeverk",
            beskrivelse = "testkodeverkbeskrivelse",
            uttømmende = true,
            verdier = listOf(
                Kodeverkverdi(
                    verdi = "testverdi",
                    visningsnavn = "testverdivisningsnavn",
                    beskrivelse = "testverdiBeskrivelse",
                    favoritt = true,
                )
            )
        )
        transactionalManager.transaction { tx ->
            fdRepository.lagre(kodeverk, tx)
        }

        transactionalManager.transaction { tx ->
            val hentetForOmråde = fdRepository.hentKodeverk(område, tx)

            assertThat(hentetForOmråde.kodeverk.size).isEqualTo(1)
            assertThat(hentetForOmråde.kodeverk[0].eksternId).isEqualTo("testkodeverk")

            val hentetForReferanse = fdRepository.hentKodeverk(Kodeverkreferanse(område.eksternId, kodeverk.eksternId), tx)

            assertThat(hentetForReferanse.eksternId).isEqualTo("testkodeverk")
        }
    }

    @Test //TODO?
    fun `kan ikke lagre feltdefinisjon på tvers av område`() {
        områdeRepository.lagre("test2")
        val område2 = områdeRepository.hent("test2")!!
        val område = områdeRepository.hent("test")!!
        val feltdefinisjon = setOf(
            Feltdefinisjon(
                eksternId = "test123",
                område = område2,
                visningsnavn = "test123",
                listetype = false,
                tolkesSom = "string",
                visTilBruker = true,
                kokriterie = true,
                kodeverkreferanse = null,
                transientFeltutleder = null,
                id = null
            )
        )
        transactionalManager.transaction { tx ->
            fdRepository.leggTil(feltdefinisjon, område, tx)
        }
    }
}