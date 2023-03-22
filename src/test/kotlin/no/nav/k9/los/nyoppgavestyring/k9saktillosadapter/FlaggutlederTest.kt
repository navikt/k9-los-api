package no.nav.k9.los.nyoppgavestyring.k9saktillosadapter

import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Ventekategori
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.koin.test.get
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FlaggutlederTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `ingen åpne aksjonspunkter og ingen steg gir ingen flagg`() {
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()

        val ventetype =
            k9SakTilLosAdapterTjeneste.utledVentetype(behandlingSteg = null, åpneAksjonspunkter = emptyList())

        assertNull(ventetype)
    }

    @Test
    fun `aktivt behandlingssteg men ingen aktive aksjonspunkter gir avventerAnnet`() {
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()

        val ventetype =
            k9SakTilLosAdapterTjeneste.utledVentetype(
                behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.toString(),
                åpneAksjonspunkter = emptyList()
            )

        assertEquals(Ventekategori.AVVENTER_ANNET, ventetype)
    }

    @Test
    fun `åpent aksjonspunkt med venteårsak gir ventekategori fra venteårsaken`() {
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()

        val ventetype = k9SakTilLosAdapterTjeneste.utledVentetype(
            behandlingSteg = BehandlingStegType.VURDER_MEDISINSKE_VILKÅR.toString(),
            åpneAksjonspunkter = listOf(
                AksjonspunktTilstandDto(
                    "9001",
                    AksjonspunktStatus.OPPRETTET,
                    Venteårsak.LEGEERKLÆRING,
                    "saksbehandler",
                    LocalDateTime.now().plusDays(1),
                    null,
                    null
                )
            )
        )

        assertEquals(Ventekategori.AVVENTER_SØKER, ventetype)
    }

    @Test
    fun `ikke aktivt behandlingssteg men åpne AP er feiltilstand`() {
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()

        assertThrows(IllegalStateException::class.java) {
            k9SakTilLosAdapterTjeneste.utledVentetype(
                behandlingSteg = null,
                åpneAksjonspunkter = listOf(
                    AksjonspunktTilstandDto(
                        "9001",
                        AksjonspunktStatus.OPPRETTET,
                        Venteårsak.LEGEERKLÆRING,
                        "saksbehandler",
                        LocalDateTime.now().plusDays(1),
                        null,
                        null
                    )
                )
            )
        }
    }

    @Test
    fun `åpent aksjonspunkt uten venteårsak gir ventekategori fra aksjonspunkt`() {
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()

        val ventetype = k9SakTilLosAdapterTjeneste.utledVentetype(
            behandlingSteg = BehandlingStegType.VURDER_MEDISINSKE_VILKÅR.getKode(),
            åpneAksjonspunkter = listOf(
                AksjonspunktTilstandDto(
                    "9001",
                    AksjonspunktStatus.OPPRETTET,
                    null,
                    "saksbehandler",
                    null,
                    null,
                    null
                )
            )
        )

        assertEquals(Ventekategori.AVVENTER_SAKSBEHANDLER, ventetype)
    }

    @Test
    fun `aktivt steg og åpne aksjonspunkt, men ingen løsbare gir avventerAnnet`() {
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()

        val ventetype = k9SakTilLosAdapterTjeneste.utledVentetype(
            behandlingSteg = BehandlingStegType.INREG_AVSL.getKode(),
            åpneAksjonspunkter = listOf(
                AksjonspunktTilstandDto(
                    "9001",
                    AksjonspunktStatus.OPPRETTET,
                    null,
                    "saksbehandler",
                    null,
                    null,
                    null
                )
            )
        )

        assertEquals(Ventekategori.AVVENTER_ANNET, ventetype)
    }
}