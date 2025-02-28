package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.mottak.saktillos

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.matchesPredicate
import no.nav.k9.los.aksjonspunktbehandling.K9SakEventDtoBuilder
import org.junit.jupiter.api.Test

class EventTilDtoMapperTest {
    @Test
    fun `5016 skal gi til beslutter`() {
        val k9SakEvent = K9SakEventDtoBuilder().hosBeslutter().build()
        val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(k9SakEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "true"} }
    }
}