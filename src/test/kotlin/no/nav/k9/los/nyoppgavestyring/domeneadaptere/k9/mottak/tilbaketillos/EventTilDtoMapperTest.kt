package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.tilbaketillos

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.matchesPredicate
import io.mockk.mockk
import no.nav.k9.los.aksjonspunktbehandling.K9TilbakeEventDtoBuilder
import org.junit.jupiter.api.Test

class EventTilDtoMapperTest {
    private val eventTilDtoMapper = EventTilDtoMapper(mockk(relaxed = true))

    @Test
    fun `5005 skal gi til beslutter`() {
        val k9TilbakeEvent = K9TilbakeEventDtoBuilder().hosBeslutter().build()
        val oppgaveDto = eventTilDtoMapper.lagOppgaveDto(k9TilbakeEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "true"} }
    }

    @Test
    fun `ikke 5005 skal ikke gi til beslutter`() {
        val k9TilbakeEvent = K9TilbakeEventDtoBuilder().foreslåVedtak().build()
        val oppgaveDto = eventTilDtoMapper.lagOppgaveDto(k9TilbakeEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "false"} }
    }
}