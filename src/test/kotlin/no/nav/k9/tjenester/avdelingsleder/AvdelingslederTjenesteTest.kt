package no.nav.k9.tjenester.avdelingsleder

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.extracting
import kotlinx.coroutines.runBlocking
import no.nav.k9.AbstractK9LosIntegrationTest
import no.nav.k9.domene.modell.KøKriterierType
import no.nav.k9.domene.modell.MerknadType
import no.nav.k9.tjenester.avdelingsleder.oppgaveko.KriteriumDto
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.util.*

internal class AvdelingslederTjenesteTest : AbstractK9LosIntegrationTest() {

    private lateinit var avdelingslederTjeneste: AvdelingslederTjeneste

    @BeforeEach
    fun setup() {
        avdelingslederTjeneste = get()
    }

    @Test
    fun `skal lagre, hente, fjerne og endre flere kriteriumDto`() {
        runBlocking {
            val id = avdelingslederTjeneste.opprettOppgaveKø()
            val køUuid = id.id
            val merknadKriterium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.MERKNADTYPE,
                inkluder = true,
                koder = listOf(MerknadType.HASTESAK.kode, MerknadType.VANSKELIG.kode)
            )

            avdelingslederTjeneste.endreKøKriterier(merknadKriterium)

            var oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(UUID.fromString(køUuid))
            assertThat(oppgaveKø.kriterier)
                .extracting { it.kriterierType }
                .containsExactly(KøKriterierType.MERKNADTYPE)

            val feilutbetalingKriterium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.FEILUTBETALING,
                inkluder = true,
                fom = 10.toString(),
                tom = 20.toString()
            )

            avdelingslederTjeneste.endreKøKriterier(feilutbetalingKriterium)

            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(UUID.fromString(køUuid))
            assertThat(oppgaveKø.kriterier)
                .extracting { it.kriterierType }
                .containsExactlyInAnyOrder(KøKriterierType.MERKNADTYPE, KøKriterierType.FEILUTBETALING)

            val fjernMerknadKrierium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.MERKNADTYPE,
                inkluder = true,
                koder = emptyList()
            )


            avdelingslederTjeneste.endreKøKriterier(fjernMerknadKrierium)
            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(UUID.fromString(køUuid))
            assertThat(oppgaveKø.kriterier)
                .extracting { it.kriterierType }
                .containsExactlyInAnyOrder(KøKriterierType.FEILUTBETALING)

        }

    }

}