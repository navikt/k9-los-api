package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.matchesPredicate
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.K9SakEventDtoBuilder
import org.junit.jupiter.api.Test

class EventTilDtoMapperTest {
    @Test
    fun `5016 skal gi til beslutter`() {
        val k9SakEvent = K9SakEventDtoBuilder().hosBeslutter().build()
        val oppgaveDto = SakEventTilOppgaveMapper.lagOppgaveDto(k9SakEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "true"} }
    }

    @Test
    fun `uten 5016 skal ikke gi til beslutter`() {
        val k9SakEvent = K9SakEventDtoBuilder().foreslåVedtak().build()
        val oppgaveDto = SakEventTilOppgaveMapper.lagOppgaveDto(k9SakEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "false"} }
    }
}