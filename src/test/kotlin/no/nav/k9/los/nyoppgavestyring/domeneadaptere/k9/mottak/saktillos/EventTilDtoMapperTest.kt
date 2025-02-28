package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.matchesPredicate
import io.mockk.mockk
import no.nav.k9.los.aksjonspunktbehandling.K9SakEventDtoBuilder
import org.junit.jupiter.api.Test

class EventTilDtoMapperTest {
    private val eventTilDtoMapper = EventTilDtoMapper(mockk(relaxed = true))

    @Test
    fun `5016 skal gi til beslutter`() {
        val k9SakEvent = K9SakEventDtoBuilder().hosBeslutter().build()
        val oppgaveDto = eventTilDtoMapper.lagOppgaveDto(k9SakEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "true"} }
    }

    @Test
    fun `uten 5016 skal ikke gi til beslutter`() {
        val k9SakEvent = K9SakEventDtoBuilder().foreslåVedtak().build()
        val oppgaveDto = eventTilDtoMapper.lagOppgaveDto(k9SakEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "false"} }
    }
}