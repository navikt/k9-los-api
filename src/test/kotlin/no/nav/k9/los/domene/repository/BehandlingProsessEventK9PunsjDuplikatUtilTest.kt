package no.nav.k9.los.domene.repository

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import no.nav.k9.los.aksjonspunktbehandling.PunsjEventDtoBuilder
import org.junit.jupiter.api.Test

class BehandlingProsessEventK9PunsjDuplikatUtilTest {
    @Test
    fun skal_fjerne_duplikat_hendelse() {
        val event1 = PunsjEventDtoBuilder().papirsøknad().build()
        assertThat(BehandlingProsessEventK9PunsjDuplikatUtil.fjernDuplikater(listOf(event1, event1, event1))).containsExactlyInAnyOrder(event1)
    }

    @Test
    fun skal_ikke_fjerne_hendelser_som_er_ulike() {
        val event1 = PunsjEventDtoBuilder().papirsøknad().build()
        val event2 = PunsjEventDtoBuilder().påVent().build()
        assertThat(BehandlingProsessEventK9PunsjDuplikatUtil.fjernDuplikater(listOf(event1, event2, event1))).containsExactlyInAnyOrder(event1, event2)
    }
}