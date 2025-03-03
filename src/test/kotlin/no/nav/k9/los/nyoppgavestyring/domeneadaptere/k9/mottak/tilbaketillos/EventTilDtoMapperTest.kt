package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.matchesPredicate
import no.nav.k9.los.aksjonspunktbehandling.K9TilbakeEventDtoBuilder
import org.junit.jupiter.api.Test

class EventTilDtoMapperTest {
    @Test
    fun `5005 skal gi til beslutter`() {
        val k9TilbakeEvent = K9TilbakeEventDtoBuilder().hosBeslutter().build()
        val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(k9TilbakeEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "true"} }
    }

    @Test
    fun `ikke 5005 skal ikke gi til beslutter`() {
        val k9TilbakeEvent = K9TilbakeEventDtoBuilder().foreslåVedtak().build()
        val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(k9TilbakeEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "false"} }
    }
}