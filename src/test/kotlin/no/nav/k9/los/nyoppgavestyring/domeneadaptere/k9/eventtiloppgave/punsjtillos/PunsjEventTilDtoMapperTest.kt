package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.punsjtillos

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.matchesPredicate
import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.punsj.PunsjEventDtoBuilder
import no.nav.k9.los.nyoppgavestyring.kodeverk.BehandlingType
import no.nav.k9.los.nyoppgavestyring.kodeverk.FagsakYtelseType
import no.nav.k9.los.nyoppgavestyring.FeltType
import no.nav.k9.los.nyoppgavestyring.OppgaveTestDataBuilder
import org.junit.jupiter.api.Test

class PunsjEventTilDtoMapperTest : AbstractK9LosIntegrationTest() {
    @Test
    fun `defaulter til ukjent`() {
        val forrigeOppgave = OppgaveTestDataBuilder().lag()
        val event = PunsjEventDtoBuilder(ytelse = null, type = null).build()
        val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(event, forrigeOppgave)
        assertThat(oppgaveDto.feltverdier)
            .any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "ytelsestype" && feltverdi.verdi == "UKJENT" } }
        assertThat(oppgaveDto.feltverdier)
            .any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "behandlingTypekode" && feltverdi.verdi == "UKJENT" } }
    }

    @Test
    fun `overskriver ukjent når ny event har noe annet`() {
        val forrigeOppgave = OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.YTELSE_TYPE, "UKJENT")
            .medOppgaveFeltVerdi(FeltType.BEHANDLING_TYPE, "UKJENT")
            .lag()
        val event = PunsjEventDtoBuilder(ytelse = FagsakYtelseType.PLEIEPENGER_SYKT_BARN, type = BehandlingType.PAPIRSØKNAD).build()
        val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(event, forrigeOppgave)
        assertThat(oppgaveDto.feltverdier)
            .any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "ytelsestype" && feltverdi.verdi == "PSB" } }
        assertThat(oppgaveDto.feltverdier)
            .any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "behandlingTypekode" && feltverdi.verdi == "PAPIRSØKNAD" } }
    }

    @Test
    fun `overskriver ikke når ny event har null`() {
        val forrigeOppgave = OppgaveTestDataBuilder()
            .medOppgaveFeltVerdi(FeltType.YTELSE_TYPE, "PSB")
            .medOppgaveFeltVerdi(FeltType.BEHANDLING_TYPE, "PAPIRSØKNAD")
            .lag()
        val event = PunsjEventDtoBuilder(ytelse = null, type = null).build()
        val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(event, forrigeOppgave)
        assertThat(oppgaveDto.feltverdier)
            .any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "ytelsestype" && feltverdi.verdi == "PSB" } }
        assertThat(oppgaveDto.feltverdier)
            .any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "behandlingTypekode" && feltverdi.verdi == "PAPIRSØKNAD" } }
    }
}