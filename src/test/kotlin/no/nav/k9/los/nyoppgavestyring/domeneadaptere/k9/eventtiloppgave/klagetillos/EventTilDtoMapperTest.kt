package no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventtiloppgave.klagetillos

import assertk.assertThat
import assertk.assertions.any
import assertk.assertions.matchesPredicate
import no.nav.k9.klage.kodeverk.Fagsystem
import no.nav.k9.klage.kodeverk.behandling.BehandlingStatus
import no.nav.k9.klage.kodeverk.behandling.BehandlingStegType
import no.nav.k9.klage.kodeverk.behandling.aksjonspunkt.AksjonspunktStatus
import no.nav.k9.klage.kodeverk.behandling.oppgavetillos.EventHendelse
import no.nav.k9.klage.kontrakt.behandling.oppgavetillos.Aksjonspunkttilstand
import no.nav.k9.klage.typer.AktørId
import no.nav.k9.klage.typer.Periode
import no.nav.k9.los.nyoppgavestyring.domeneadaptere.k9.eventmottak.klage.K9KlageEventDto
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*

class EventTilDtoMapperTest {
    @Test
    fun `5016 skal gi til beslutter`() {
        val k9KlageEvent = K9KlageEventDto(
            eksternId = UUID.randomUUID(),
            fagsystem = Fagsystem.K9SAK,
            eventTid = LocalDateTime.now(),
            opprettetBehandling = LocalDateTime.now(),
            aksjonspunkttilstander = listOf(
                Aksjonspunkttilstand(
                    "5016",
                    AksjonspunktStatus.OPPRETTET,
                    null,
                    null,
                    LocalDateTime.now(),
                    LocalDateTime.now()
                )
            ),
            påklagdBehandlingType = null,
            påklagdBehandlingId = null,
            utenlandstilsnitt = null,
            behandlingstidFrist = LocalDate.now(),
            saksnummer = "test",
            aktørId = "test",
            eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
            behandlingStatus = BehandlingStatus.OPPRETTET.kode,
            behandlingSteg = BehandlingStegType.VURDER_KLAGE_FØRSTEINSTANS.kode,
            behandlendeEnhet = "test",
            ansvarligBeslutter = "test",
            ansvarligSaksbehandler = "test",
            resultatType = "test",
            ytelseTypeKode = "test",
            behandlingTypeKode = "test",
            fagsakPeriode = Periode(LocalDate.now().minusDays(1), LocalDate.now()),
            pleietrengendeAktørId = AktørId(2L),
            relatertPartAktørId = AktørId(3L),
            vedtaksdato = LocalDate.now(),
            behandlingsårsaker = listOf("test"),
        )


        val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(k9KlageEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "true" } }
    }

    @Test
    fun `uten 5016 skal ikke gi til beslutter`() {
        val k9KlageEvent = K9KlageEventDto(
            eksternId = UUID.randomUUID(),
            fagsystem = Fagsystem.K9SAK,
            eventTid = LocalDateTime.now(),
            opprettetBehandling = LocalDateTime.now(),
            aksjonspunkttilstander = listOf(),
            påklagdBehandlingType = null,
            påklagdBehandlingId = null,
            utenlandstilsnitt = null,
            behandlingstidFrist = LocalDate.now(),
            saksnummer = "test",
            aktørId = "test",
            eventHendelse = EventHendelse.AKSJONSPUNKT_OPPRETTET,
            behandlingStatus = BehandlingStatus.OPPRETTET.kode,
            behandlingSteg = BehandlingStegType.VURDER_KLAGE_FØRSTEINSTANS.kode,
            behandlendeEnhet = "test",
            ansvarligBeslutter = "test",
            ansvarligSaksbehandler = "test",
            resultatType = "test",
            ytelseTypeKode = "test",
            behandlingTypeKode = "test",
            fagsakPeriode = Periode(LocalDate.now().minusDays(1), LocalDate.now()),
            pleietrengendeAktørId = AktørId(2L),
            relatertPartAktørId = AktørId(3L),
            vedtaksdato = LocalDate.now(),
            behandlingsårsaker = listOf("test"),
        )
        val oppgaveDto = EventTilDtoMapper.lagOppgaveDto(k9KlageEvent, null)

        assertThat(oppgaveDto.feltverdier).any { it.matchesPredicate { feltverdi -> feltverdi.nøkkel == "liggerHosBeslutter" && feltverdi.verdi == "false" } }
    }
}