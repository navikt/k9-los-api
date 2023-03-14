package no.nav.k9.los.nyoppgavestyring.k9saktillosadapter

import no.nav.k9.kodeverk.behandling.BehandlingStegType
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.kodeverk.behandling.aksjonspunkt.Venteårsak
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9saktillos.K9SakTilLosAdapterTjeneste
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdiDto
import no.nav.k9.sak.kontrakt.aksjonspunkt.AksjonspunktTilstandDto
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertThrows
import org.koin.test.get
import java.time.LocalDateTime
import kotlin.IllegalStateException
import kotlin.test.assertEquals

class FlaggutlederTest : AbstractK9LosIntegrationTest() {

    @Test
    fun `ingen åpne aksjonspunkter og ingen steg gir ingen flagg`() {
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()
        val feltverdier = mutableListOf<OppgaveFeltverdiDto>()

        k9SakTilLosAdapterTjeneste.utledAvventerflagg(
            behandlingSteg = null, åpneAksjonspunkter = emptyList(), oppgaveFeltverdiDtos = feltverdier
        )

        assertEquals(k9SakTilLosAdapterTjeneste.avventerIngen(), feltverdier)
    }

    @Test
    fun `aktivt behandlingssteg men ingen aktive aksjonspunkter gir avventerAnnet`() {
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()
        val feltverdier = mutableListOf<OppgaveFeltverdiDto>()

        k9SakTilLosAdapterTjeneste.utledAvventerflagg(
            behandlingSteg = BehandlingStegType.INNHENT_REGISTEROPP.toString(),
            åpneAksjonspunkter = emptyList(),
            oppgaveFeltverdiDtos = feltverdier
        )

        assertEquals(k9SakTilLosAdapterTjeneste.avventerAnnet(), feltverdier)
    }

    @Test
    fun `åpent aksjonspunkt med venteårsak gir ventekategori fra venteårsaken`() {
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()
        val feltverdier = mutableListOf<OppgaveFeltverdiDto>()

        k9SakTilLosAdapterTjeneste.utledAvventerflagg(
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
            ),
            oppgaveFeltverdiDtos = feltverdier
        )

        assertEquals(k9SakTilLosAdapterTjeneste.avventerSøker(), feltverdier)
    }

    @Test
    fun `ikke aktivt behandlingssteg men åpne AP er feiltilstand`() {
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()
        val feltverdier = mutableListOf<OppgaveFeltverdiDto>()

        assertThrows(IllegalStateException::class.java) {
            k9SakTilLosAdapterTjeneste.utledAvventerflagg(
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
                ),
                oppgaveFeltverdiDtos = feltverdier
            )
        }
    }

    @Test
    fun `åpent aksjonspunkt uten venteårsak gir ventekategori fra aksjonspunkt`() {
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()
        val feltverdier = mutableListOf<OppgaveFeltverdiDto>()

        k9SakTilLosAdapterTjeneste.utledAvventerflagg(
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
            ),
            oppgaveFeltverdiDtos = feltverdier
        )

        assertEquals(k9SakTilLosAdapterTjeneste.avventerSaksbehandler(), feltverdier)
    }

    @Test
    fun `aktivt steg og åpne aksjonspunkt, men ingen løsbare er feiltilstand`() {
        val k9SakTilLosAdapterTjeneste = get<K9SakTilLosAdapterTjeneste>()
        val feltverdier = mutableListOf<OppgaveFeltverdiDto>()

        assertThrows(IllegalStateException::class.java) {
            k9SakTilLosAdapterTjeneste.utledAvventerflagg(
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
                ),
                oppgaveFeltverdiDtos = feltverdier
            )
        }
    }
}