package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.klagetillos

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.matchesPredicate
import no.nav.k9.klage.kodeverk.Fagsystem
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.Aksjonspunkttilstand
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.KlagebehandlingProsessHendelse
import no.nav.k9.los.fagsystem.FagsystemModul
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.*

class EventTilDtoMapperTest {
    @Test
    fun `5016 skal gi til beslutter`() {
        val k9KlageEvent = KlagebehandlingProsessHendelse.builder()
            .medEksternId(UUID.randomUUID())
            .medFagsystem(Fagsystem.K9SAK)
            .medEventTid(LocalDateTime.now())
            .medOpprettetBehandling(LocalDateTime.now())
            .medAksjonspunktTilstander(
                listOf(
                    Aksjonspunkttilstand("5016", no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus.OPPRETTET, null, null, LocalDateTime.now(), LocalDateTime.now())
                )
            ).build()
        val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(k9KlageEvent, null, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "true"} }
    }

    @Test
    fun `uten 5016 skal ikke gi til beslutter`() {
        val k9KlageEvent = KlagebehandlingProsessHendelse.builder()
            .medEksternId(UUID.randomUUID())
            .medFagsystem(Fagsystem.K9SAK)
            .medEventTid(LocalDateTime.now())
            .medOpprettetBehandling(LocalDateTime.now())
            .medAksjonspunktTilstander(
                listOf()
            )
            .build()
        val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(k9KlageEvent, null, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "false"} }
    }
}