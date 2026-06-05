package no.nav.k9.los.domeneadaptere.k9.eventtiloppgave.tilbaketillos

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.containsOnly
import assertk.assertions.matchesPredicate
import no.nav.k9.los.domeneadaptere.k9.eventmottak.K9TilbakeEventDtoBuilder
import no.nav.k9.los.domeneadaptere.k9.eventmottak.tilbakekrav.AksjonspunktDefinisjonK9Tilbake
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdiDto
import org.junit.jupiter.api.Test

private fun verdierFor(feltverdier: List<OppgaveFeltverdiDto>, nøkkel: String): List<String?> =
    feltverdier.filter { it.nøkkel == nøkkel }.map { it.verdi }

class EventTilDtoMapperTest {
    @Test
    fun `5005 skal gi til beslutter`() {
        val k9TilbakeEvent = K9TilbakeEventDtoBuilder().hosBeslutter().build()
        val oppgaveDto = TilbakeEventTilOppgaveMapper.lagOppgaveDto(k9TilbakeEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "true"} }
    }

    @Test
    fun `ikke 5005 skal ikke gi til beslutter`() {
        val k9TilbakeEvent = K9TilbakeEventDtoBuilder().foreslåVedtak().build()
        val oppgaveDto = TilbakeEventTilOppgaveMapper.lagOppgaveDto(k9TilbakeEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "false"} }
    }

    @Test
    fun `foreslåVedtak - utførte og løsbare aksjonspunkter, ingen avbrutte`() {
        val event = K9TilbakeEventDtoBuilder().foreslåVedtak().build()
        val oppgaveDto = TilbakeEventTilOppgaveMapper.lagOppgaveDto(event, null)
        val feltverdier = oppgaveDto.feltverdier

        // FORESLÅ_VEDTAK (5004) er OPPRETTET og manuelt → løsbart
        assertThat(verdierFor(feltverdier, "løsbartAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjonK9Tilbake.FORESLÅ_VEDTAK.kode
        )

        // VURDER_TILBAKEKREVING (5002), VURDER_FORELDELSE (5003), VENT_PÅ_BRUKERTILBAKEMELDING (7001), AVKLART_FAKTA_FEILUTBETALING (7003) er UTFØRT
        assertThat(verdierFor(feltverdier, "utførtAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjonK9Tilbake.VURDER_TILBAKEKREVING.kode,
            AksjonspunktDefinisjonK9Tilbake.VURDER_FORELDELSE.kode,
            AksjonspunktDefinisjonK9Tilbake.VENT_PÅ_BRUKERTILBAKEMELDING.kode,
            AksjonspunktDefinisjonK9Tilbake.AVKLART_FAKTA_FEILUTBETALING.kode,
        )

        // Ingen avbrutte
        assertThat(verdierFor(feltverdier, "avbruttAksjonspunkt")).containsOnly(null)
    }

    @Test
    fun `returFraBeslutter - utførte, avbrutte og løsbare aksjonspunkter`() {
        val event = K9TilbakeEventDtoBuilder().returFraBeslutter().build()
        val oppgaveDto = TilbakeEventTilOppgaveMapper.lagOppgaveDto(event, null)
        val feltverdier = oppgaveDto.feltverdier

        // FORESLÅ_VEDTAK (5004) er OPPRETTET og manuelt → løsbart
        assertThat(verdierFor(feltverdier, "løsbartAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjonK9Tilbake.FORESLÅ_VEDTAK.kode
        )

        // VURDER_TILBAKEKREVING, VURDER_FORELDELSE, VENT_PÅ_BRUKERTILBAKEMELDING, AVKLART_FAKTA_FEILUTBETALING er UTFØRT
        assertThat(verdierFor(feltverdier, "utførtAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjonK9Tilbake.VURDER_TILBAKEKREVING.kode,
            AksjonspunktDefinisjonK9Tilbake.VURDER_FORELDELSE.kode,
            AksjonspunktDefinisjonK9Tilbake.VENT_PÅ_BRUKERTILBAKEMELDING.kode,
            AksjonspunktDefinisjonK9Tilbake.AVKLART_FAKTA_FEILUTBETALING.kode,
        )

        // FATTE_VEDTAK (5005) er AVBRUTT
        assertThat(verdierFor(feltverdier, "avbruttAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjonK9Tilbake.FATTE_VEDTAK.kode
        )
    }

    @Test
    fun `avsluttet - alle aksjonspunkter utført, ingen avbrutte`() {
        val event = K9TilbakeEventDtoBuilder().avsluttet().build()
        val oppgaveDto = TilbakeEventTilOppgaveMapper.lagOppgaveDto(event, null)
        val feltverdier = oppgaveDto.feltverdier

        // Ingen OPPRETTET → løsbartAksjonspunkt er null
        assertThat(verdierFor(feltverdier, "løsbartAksjonspunkt")).containsOnly(null)

        // Alle er UTFØRT
        assertThat(verdierFor(feltverdier, "utførtAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjonK9Tilbake.VURDER_TILBAKEKREVING.kode,
            AksjonspunktDefinisjonK9Tilbake.VURDER_FORELDELSE.kode,
            AksjonspunktDefinisjonK9Tilbake.FORESLÅ_VEDTAK.kode,
            AksjonspunktDefinisjonK9Tilbake.FATTE_VEDTAK.kode,
            AksjonspunktDefinisjonK9Tilbake.VENT_PÅ_BRUKERTILBAKEMELDING.kode,
            AksjonspunktDefinisjonK9Tilbake.VENT_PÅ_TILBAKEKREVINGSGRUNNLAG.kode,
            AksjonspunktDefinisjonK9Tilbake.AVKLART_FAKTA_FEILUTBETALING.kode,
        )

        // Ingen avbrutte
        assertThat(verdierFor(feltverdier, "avbruttAksjonspunkt")).containsOnly(null)
    }

    @Test
    fun `hosBeslutter - FATTE_VEDTAK løsbart, resten utført`() {
        val event = K9TilbakeEventDtoBuilder().hosBeslutter().build()
        val oppgaveDto = TilbakeEventTilOppgaveMapper.lagOppgaveDto(event, null)
        val feltverdier = oppgaveDto.feltverdier

        // FATTE_VEDTAK (5005) er OPPRETTET og manuelt → løsbart
        assertThat(verdierFor(feltverdier, "løsbartAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjonK9Tilbake.FATTE_VEDTAK.kode
        )

        // Resten er UTFØRT
        assertThat(verdierFor(feltverdier, "utførtAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjonK9Tilbake.VURDER_TILBAKEKREVING.kode,
            AksjonspunktDefinisjonK9Tilbake.VURDER_FORELDELSE.kode,
            AksjonspunktDefinisjonK9Tilbake.FORESLÅ_VEDTAK.kode,
            AksjonspunktDefinisjonK9Tilbake.VENT_PÅ_BRUKERTILBAKEMELDING.kode,
            AksjonspunktDefinisjonK9Tilbake.AVKLART_FAKTA_FEILUTBETALING.kode,
        )

        // Ingen avbrutte
        assertThat(verdierFor(feltverdier, "avbruttAksjonspunkt")).containsOnly(null)
    }

    @Test
    fun `opprettet - VENT_PÅ_BRUKERTILBAKEMELDING utført, AVKLART_FAKTA løsbart`() {
        val event = K9TilbakeEventDtoBuilder().opprettet().build()
        val oppgaveDto = TilbakeEventTilOppgaveMapper.lagOppgaveDto(event, null)
        val feltverdier = oppgaveDto.feltverdier

        // AVKLART_FAKTA_FEILUTBETALING (7003) er OPPRETTET og er totrinn (ikke autopunkt) → løsbart
        assertThat(verdierFor(feltverdier, "løsbartAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjonK9Tilbake.AVKLART_FAKTA_FEILUTBETALING.kode
        )

        // VENT_PÅ_BRUKERTILBAKEMELDING er UTFØRT
        assertThat(verdierFor(feltverdier, "utførtAksjonspunkt")).containsOnly(
            AksjonspunktDefinisjonK9Tilbake.VENT_PÅ_BRUKERTILBAKEMELDING.kode,
        )

        // Ingen avbrutte
        assertThat(verdierFor(feltverdier, "avbruttAksjonspunkt")).containsOnly(null)
    }
}