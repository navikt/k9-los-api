package no.nav.k9.los.domene.repository

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import no.nav.k9.los.aksjonspunktbehandling.K9SakEventDtoBuilder
import org.junit.jupiter.api.Test

class BehandlingProsessEventK9DuplikatUtilTest {

    @Test
    fun skal_fjerne_duplikat_hendelse() {
        val event1 = K9SakEventDtoBuilder().opprettet().build()
        assertThat(BehandlingProsessEventK9DuplikatUtil.fjernDuplikater(listOf(event1, event1, event1))).containsExactlyInAnyOrder(event1)
    }

    @Test
    fun skal_ikke_fjerne_hendelser_som_er_ulike() {
        val event1 = K9SakEventDtoBuilder().opprettet().build()
        val event2 = K9SakEventDtoBuilder().foreslåVedtak().build()
        assertThat(BehandlingProsessEventK9DuplikatUtil.fjernDuplikater(listOf(event1, event2, event1))).containsExactlyInAnyOrder(event1, event2)
    }

    @Test
    fun skal_fjerne_hendelse_hvor_resultattype_er_null_på_den_ene_og_IKKE_FASTSATT_på_den_andre_og_ellers_identisk() {
        val x = K9SakEventDtoBuilder().opprettet().build()
        val event1 = x.copy(resultatType = null)
        val event2 = x.copy(resultatType = "IKKE_FASTSATT")

        assertThat(BehandlingProsessEventK9DuplikatUtil.fjernDuplikater(listOf(event1, event2))).containsExactlyInAnyOrder(event1)
    }
}