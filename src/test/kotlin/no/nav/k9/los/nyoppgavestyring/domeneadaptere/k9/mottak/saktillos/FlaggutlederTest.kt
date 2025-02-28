package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos

import io.mockk.mockk
import no.nav.k9.kodeverk.behandling.BehandlingStatus
import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Ventekategori
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FlaggutlederTest {
    private val eventTilDtoMapper = EventTilDtoMapper(mockk())

    @Test
    fun `avsluttet behandling og ingen steg gir ingen flagg`() {
        val ventetype =
            eventTilDtoMapper.utledVentetype(
                behandlingSteg = null,
                behandlingStatus = BehandlingStatus.AVSLUTTET.kode,
                åpneAksjonspunkter = emptyList()
            )

        assertNull(ventetype)
    }

    @Test
    fun `aktivt behandlingssteg men ingen aktive aksjonspunkter gir avventerAnnet`() {
        val ventetype =
            eventTilDtoMapper.utledVentetype(
                behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.toString(),
                behandlingStatus = null,
                åpneAksjonspunkter = emptyList()
            )

        assertEquals(Ventekategori.AVVENTER_ANNET, ventetype)
    }

    @Test
    fun `åpent aksjonspunkt med venteårsak gir ventekategori fra venteårsaken`() {
        val ventetype = eventTilDtoMapper.utledVentetype(
            behandlingSteg = BehandlingStegType.VURDER_MEDISINSKE_VILKÅR.toString(),
            behandlingStatus = null,
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
    fun `avsluttet behandling, men ingen AP gir avventer annet`() {
        val ventetype = eventTilDtoMapper.utledVentetype(
            behandlingSteg = null,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
            åpneAksjonspunkter = emptyList()
        )

        assertEquals(Ventekategori.AVVENTER_ANNET, ventetype)
    }

    @Test
    fun `åpent aksjonspunkt uten venteårsak gir ventekategori fra aksjonspunkt`() {
        val ventetype = eventTilDtoMapper.utledVentetype(
            behandlingSteg = BehandlingStegType.VURDER_MEDISINSKE_VILKÅR.getKode(),
            behandlingStatus = null,
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
        val ventetype = eventTilDtoMapper.utledVentetype(
            behandlingSteg = BehandlingStegType.INREG_AVSL.getKode(),
            behandlingStatus = null,
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

    @Test
    fun `forvent Avventer Annet hvis ingen aksjonspunkter med ventefrist og -årsak og behandlingen er åpen, men ingen steg er aktive`() {
        val ventetype = eventTilDtoMapper.utledVentetype(
            behandlingSteg = null,
            behandlingStatus = BehandlingStatus.UTREDES.kode,
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