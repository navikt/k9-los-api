package no.nav.k9.los.domeneadaptere.k9.eventtiloppgave.saktillos

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsOnly
import assertk.assertions.isEmpty
import assertk.assertions.matchesPredicate
import no.nav.k9.kodeverk.behandling.aksjonspunkt.AksjonspunktDefinisjon
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.domeneadaptere.k9.eventmottak.EventHendelse
import no.nav.k9.los.domeneadaptere.k9.eventmottak.K9SakEventDtoBuilder
import no.nav.k9.los.domeneadaptere.k9.eventmottak.eventlager.EventLagret
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdiDto
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.koin.test.get

private fun verdierFor(feltverdier: List<OppgaveFeltverdiDto>, nøkkel: String): List<String?> =
    feltverdier.filter { it.nøkkel == nøkkel }.map { it.verdi }

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

        assertTrue(innsending is no.nav.k9.los.oppgavemottak.VaskOppgaveversjon)
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

        assertTrue(innsending is no.nav.k9.los.oppgavemottak.NyOppgaveversjon)
    }

    @Test
    fun `returFraBeslutter gir riktige aksjonspunktfelt`() {
        val event = K9SakEventDtoBuilder().returFraBeslutter().build()
        val oppgaveDto = SakEventTilOppgaveMapper.lagOppgaveDto(event, null)
        val feltverdier = oppgaveDto.feltverdier

        // KONTROLLER_LEGEERKLÆRING=OPPRETTET, FORESLÅ_VEDTAK=AVBRUTT, FATTER_VEDTAK=UTFØRT
        // behandlingSteg=FORESLÅ_VEDTAK

        assertThat(verdierFor(feltverdier, "utførtAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjon.FATTER_VEDTAK.kode
        )
        assertThat(verdierFor(feltverdier, "avbruttAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjon.FORESLÅ_VEDTAK.kode
        )
        // KONTROLLER_LEGEERKLÆRING er OPPRETTET men i annet steg enn FORESLÅ_VEDTAK → fremtidig
        assertThat(verdierFor(feltverdier, "fremtidigAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING.kode
        )
        // Ingen OPPRETTET AP matcher nåværende steg → løsbartAksjonspunkt ikke satt
        assertThat(verdierFor(feltverdier, "løsbartAksjonspunkt")).isEmpty()

        // Eksisterende felt: alle aksjonspunkter uavhengig av status
        assertThat(verdierFor(feltverdier, "aksjonspunkt")).containsOnly(
            AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING.kode,
            AksjonspunktDefinisjon.FORESLÅ_VEDTAK.kode,
            AksjonspunktDefinisjon.FATTER_VEDTAK.kode
        )
        // Eksisterende felt: kun OPPRETTET aksjonspunkter
        assertThat(verdierFor(feltverdier, "aktivtAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING.kode
        )
    }

    @Test
    fun `hosBeslutter gir riktige aksjonspunktfelt`() {
        val event = K9SakEventDtoBuilder().hosBeslutter().build()
        val oppgaveDto = SakEventTilOppgaveMapper.lagOppgaveDto(event, null)
        val feltverdier = oppgaveDto.feltverdier

        // KONTROLLER_LEGEERKLÆRING=UTFØRT, FORESLÅ_VEDTAK=UTFØRT, FATTER_VEDTAK=OPPRETTET
        // behandlingSteg=FATTE_VEDTAK

        assertThat(verdierFor(feltverdier, "utførtAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING.kode,
            AksjonspunktDefinisjon.FORESLÅ_VEDTAK.kode
        )
        assertThat(verdierFor(feltverdier, "avbruttAksjonspunkt")).containsOnly(null)
        // FATTER_VEDTAK er OPPRETTET og i nåværende steg → ikke fremtidig
        assertThat(verdierFor(feltverdier, "fremtidigAksjonspunkt")).containsOnly(null)
        // FATTER_VEDTAK er løsbart (manuell, matcher steget)
        assertThat(verdierFor(feltverdier, "løsbartAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjon.FATTER_VEDTAK.kode
        )
    }

    @Test
    fun `avsluttet gir kun utførte aksjonspunktfelt`() {
        val event = K9SakEventDtoBuilder().avsluttet().build()
        val oppgaveDto = SakEventTilOppgaveMapper.lagOppgaveDto(event, null)
        val feltverdier = oppgaveDto.feltverdier

        // Alle tre aksjonspunkter er UTFØRT
        assertThat(verdierFor(feltverdier, "utførtAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING.kode,
            AksjonspunktDefinisjon.FORESLÅ_VEDTAK.kode,
            AksjonspunktDefinisjon.FATTER_VEDTAK.kode
        )
        assertThat(verdierFor(feltverdier, "avbruttAksjonspunkt")).containsOnly(null)
        assertThat(verdierFor(feltverdier, "fremtidigAksjonspunkt")).containsOnly(null)
    }

    @Test
    fun `opprettet uten aksjonspunkter gir tomme aksjonspunktfelt`() {
        val event = K9SakEventDtoBuilder().opprettet().build()
        val oppgaveDto = SakEventTilOppgaveMapper.lagOppgaveDto(event, null)
        val feltverdier = oppgaveDto.feltverdier

        assertThat(verdierFor(feltverdier, "utførtAksjonspunkt")).containsOnly(null)
        assertThat(verdierFor(feltverdier, "avbruttAksjonspunkt")).containsOnly(null)
        assertThat(verdierFor(feltverdier, "fremtidigAksjonspunkt")).containsOnly(null)
    }

    @Test
    fun `returFraBeslutterOpptjening gir riktige aksjonspunktfelt med alle tre statuser og autopunkt`() {
        val event = K9SakEventDtoBuilder().returFraBeslutterOpptjening().build()
        val oppgaveDto = SakEventTilOppgaveMapper.lagOppgaveDto(event, null)
        val feltverdier = oppgaveDto.feltverdier

        // VURDER_OPPTJENINGSVILKÅRET=OPPRETTET, FASTSETT_BEREGNINGSGRUNNLAG_AT_FL=AVBRUTT,
        // KONTROLLER_LEGEERKLÆRING=UTFØRT, FORESLÅ_VEDTAK=AVBRUTT, FATTER_VEDTAK=UTFØRT,
        // AUTO_MANUELT_SATT_PÅ_VENT=UTFØRT (autopunkt)
        // behandlingSteg=VURDER_OPPTJENINGSVILKÅR

        assertThat(verdierFor(feltverdier, "utførtAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjon.KONTROLLER_LEGEERKLÆRING.kode,
            AksjonspunktDefinisjon.FATTER_VEDTAK.kode,
            AksjonspunktDefinisjon.AUTO_MANUELT_SATT_PÅ_VENT.kode
        )
        assertThat(verdierFor(feltverdier, "avbruttAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjon.FASTSETT_BEREGNINGSGRUNNLAG_ARBEIDSTAKER_FRILANS.kode,
            AksjonspunktDefinisjon.FORESLÅ_VEDTAK.kode
        )
        // VURDER_OPPTJENINGSVILKÅRET er OPPRETTET og i nåværende steg → ikke fremtidig
        assertThat(verdierFor(feltverdier, "fremtidigAksjonspunkt")).containsOnly(null)
        // VURDER_OPPTJENINGSVILKÅRET er løsbart
        assertThat(verdierFor(feltverdier, "løsbartAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjon.VURDER_OPPTJENINGSVILKÅRET.kode
        )
    }
}