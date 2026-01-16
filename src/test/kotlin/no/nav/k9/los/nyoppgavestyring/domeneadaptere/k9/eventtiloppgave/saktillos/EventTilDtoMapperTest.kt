package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.saktillos

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.matchesPredicate
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.K9SakEventDtoBuilder
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.nyoppgavestyring.infrastruktur.utils.LosObjectMapper
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.test.get

class EventTilDtoMapperTest: AbstractK9LosIntegrationTest() {
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

    @Test
    fun `Melding med eventhendelse VASKEEVENT skal gjøre at oppgaveDto pakkes inn i VaskOppgaveversjon`() {
        val k9SakEvent = K9SakEventDtoBuilder().foreslåVedtak().build().copy(eventHendelse = EventHendelse.VASKEEVENT)
        val sakEventTilOppgaveMapper: SakEventTilOppgaveMapper = get()
        val innsending = sakEventTilOppgaveMapper.lagOppgaveDto(
            EventLagret.K9Sak(
                eksternId = k9SakEvent.eksternId.toString(),
                eventJson = LosObjectMapper.instance.writeValueAsString(k9SakEvent),
                dirty = true,
                nøkkelId = 0,
                eksternVersjon = k9SakEvent.eventTid.toString(),
                opprettet = k9SakEvent.eventTid,
            ), null, 0)

        assertTrue(innsending is no.nav.k9.los.nyoppgavestyring.mottak.oppgave.VaskOppgaveversjon)
    }

    @Test
    fun `Melding med eventhendelse annet enn VASKEEVENT skal gjøre at oppgaveDto pakkes inn i NyOppgaveversjon`() {
        val k9SakEvent = K9SakEventDtoBuilder().foreslåVedtak().build()
        val sakEventTilOppgaveMapper: SakEventTilOppgaveMapper = get()
        val innsending = sakEventTilOppgaveMapper.lagOppgaveDto(
            EventLagret.K9Sak(
                eksternId = k9SakEvent.eksternId.toString(),
                eventJson = LosObjectMapper.instance.writeValueAsString(k9SakEvent),
                dirty = true,
                nøkkelId = 0,
                eksternVersjon = k9SakEvent.eventTid.toString(),
                opprettet = k9SakEvent.eventTid,
            ), null, 0)

        assertTrue(innsending is no.nav.k9.los.nyoppgavestyring.mottak.oppgave.NyOppgaveversjon)
    }
}