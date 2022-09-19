package no.nav.k9.tjenester.avdelingsleder

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.extracting
import assertk.assertions.isEmpty
import kotlinx.coroutines.runBlocking
import no.nav.k9.AbstractK9LosIntegrationTest
import no.nav.k9.domene.modell.KøKriterierType
import no.nav.k9.domene.modell.MerknadType
import no.nav.k9.domene.modell.OppgaveKode
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
                koder = listOf(MerknadType.HASTESAK.kode, MerknadType.VANSKELIG.kode)
            )

            avdelingslederTjeneste.endreKøKriterier(merknadKriterium)

            val køid = UUID.fromString(køUuid)
            var oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier)
                .extracting { it.kriterierType }
                .containsExactly(KøKriterierType.MERKNADTYPE)

            val feilutbetalingKriterium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.FEILUTBETALING,
                fom = 10.toString(),
                tom = 20.toString()
            )

            avdelingslederTjeneste.endreKøKriterier(feilutbetalingKriterium)

            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier)
                .extracting { it.kriterierType }
                .containsExactlyInAnyOrder(KøKriterierType.MERKNADTYPE, KøKriterierType.FEILUTBETALING)

            val fjernMerknadKrierium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.MERKNADTYPE,
                koder = emptyList()
            )

            avdelingslederTjeneste.endreKøKriterier(fjernMerknadKrierium)
            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier)
                .extracting { it.kriterierType }
                .containsExactlyInAnyOrder(KøKriterierType.FEILUTBETALING)

            val fjernFeilUtbetalingKriterium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.FEILUTBETALING,
                fom = 10.toString(),
                tom = 20.toString(),
                checked = false
            )

            avdelingslederTjeneste.endreKøKriterier(fjernFeilUtbetalingKriterium)
            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier).isEmpty()


            val oppgavekodeKriterium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.OPPGAVEKODE,
                koder = listOf(OppgaveKode.SYKDOM.kode, OppgaveKode.ENDELIG_AVKLARING_MANGLER_IM.kode)
            )

            avdelingslederTjeneste.endreKøKriterier(oppgavekodeKriterium)

            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier)
                .extracting { it.kriterierType }
                .containsExactlyInAnyOrder(KøKriterierType.OPPGAVEKODE)

            val fjernOppgaveKodeKriterium = KriteriumDto(
                id = køUuid,
                kriterierType = KøKriterierType.OPPGAVEKODE,
                koder = emptyList()
            )

            avdelingslederTjeneste.endreKøKriterier(fjernOppgaveKodeKriterium)
            oppgaveKø = avdelingslederTjeneste.hentOppgaveKø(køid)
            assertThat(oppgaveKø.kriterier).isEmpty()
        }

    }

}