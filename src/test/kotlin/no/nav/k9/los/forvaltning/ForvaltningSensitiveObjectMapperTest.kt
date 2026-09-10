package no.nav.k9.los.forvaltning

import no.nav.k9.los.domeneadaptere.eventmottak.ung.sak.UngSakEventDto
import no.nav.k9.los.domeneadaptere.k9.eventmottak.K9SakEventDtoBuilder
import no.nav.k9.los.domeneadaptere.k9.eventmottak.K9TilbakeEventDtoBuilder
import no.nav.k9.los.domeneadaptere.eventmottak.k9.klage.K9KlageEventDto
import no.nav.k9.los.domeneadaptere.k9.eventmottak.punsj.PunsjEventDtoBuilder
import no.nav.ung.kodeverk.Fagsystem
import no.nav.ung.kodeverk.hendelse.EventHendelse
import no.nav.k9.klage.kodeverk.Fagsystem as KlageFagsystem
import no.nav.k9.klage.kodeverk.behandling.oppgavetillos.EventHendelse as KlageEventHendelse
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.time.LocalDate
import java.util.UUID

class ForvaltningSensitiveObjectMapperTest {

    @Test
    fun `ungsak event serialization skjuler sensitive felter`() {
        val dto = UngSakEventDto(
            eksternId = UUID.randomUUID(),
            fagsystem = Fagsystem::class.java.enumConstants.first(),
            saksnummer = "SAK-1",
            aktørId = "1234567890",
            eventTid = LocalDateTime.now(),
            eventHendelse = EventHendelse::class.java.enumConstants.first(),
            behandlingStatus = "UNDER_BEHANDLING",
            ytelseTypeKode = "UNG",
            behandlingTypeKode = "TYPE",
            opprettetBehandling = LocalDateTime.now(),
        )

        val json = ForvaltningSensitiveObjectMapper.prettyInstance.writeValueAsString(dto)

        assertFalse(json.contains("aktørId"))
        assertFalse(json.contains("1234567890"))
        assertTrue(json.contains("saksnummer"))
    }

    @Test
    fun `k9sak event serialization skjuler aktor-id felter`() {
        val dto = K9SakEventDtoBuilder().build().copy(
            aktørId = "AKTOR-1",
            pleietrengendeAktørId = "AKTOR-2",
            relatertPartAktørId = "AKTOR-3",
        )

        val json = ForvaltningSensitiveObjectMapper.prettyInstance.writeValueAsString(dto)

        assertFalse(json.contains("aktørId"))
        assertFalse(json.contains("pleietrengendeAktørId"))
        assertFalse(json.contains("relatertPartAktørId"))
        assertFalse(json.contains("AKTOR-1"))
        assertTrue(json.contains("saksnummer"))
    }

    @Test
    fun `k9tilbake event serialization skjuler aktor-id`() {
        val dto = K9TilbakeEventDtoBuilder().build().copy(aktørId = "AKTOR-1")

        val json = ForvaltningSensitiveObjectMapper.prettyInstance.writeValueAsString(dto)

        assertFalse(json.contains("aktørId"))
        assertFalse(json.contains("AKTOR-1"))
        assertTrue(json.contains("saksnummer"))
    }

    @Test
    fun `k9punsj event serialization skjuler aktor-id felter`() {
        val dto = PunsjEventDtoBuilder().build().copy(
            aktørId = no.nav.k9.sak.typer.AktørId("AKTOR-1"),
            pleietrengendeAktørId = "AKTOR-2",
        )

        val json = ForvaltningSensitiveObjectMapper.prettyInstance.writeValueAsString(dto)

        assertFalse(json.contains("aktørId"))
        assertFalse(json.contains("pleietrengendeAktørId"))
        assertFalse(json.contains("AKTOR-1"))
        assertTrue(json.contains("journalpostId"))
    }

    @Test
    fun `k9klage event serialization skjuler aktor-id felter`() {
        val dto = K9KlageEventDto(
            eksternId = UUID.randomUUID(),
            påklagdBehandlingId = null,
            påklagdBehandlingType = null,
            fagsystem = KlageFagsystem::class.java.enumConstants.first(),
            utenlandstilsnitt = null,
            behandlingstidFrist = LocalDate.now(),
            saksnummer = "SAK-2",
            aktørId = "AKTOR-1",
            eventTid = LocalDateTime.now(),
            eventHendelse = KlageEventHendelse::class.java.enumConstants.first(),
            behandlingStatus = "OPPRETTET",
            behandlingSteg = null,
            behandlendeEnhet = null,
            ansvarligBeslutter = null,
            ansvarligSaksbehandler = null,
            resultatType = null,
            ytelseTypeKode = "YTELSE",
            behandlingTypeKode = "TYPE",
            opprettetBehandling = LocalDateTime.now(),
            fagsakPeriode = null,
            pleietrengendeAktørId = null,
            relatertPartAktørId = null,
            aksjonspunkttilstander = emptyList(),
            vedtaksdato = null,
            behandlingsårsaker = emptyList(),
        )

        val json = ForvaltningSensitiveObjectMapper.prettyInstance.writeValueAsString(dto)

        assertFalse(json.contains("aktørId"))
        assertFalse(json.contains("pleietrengendeAktørId"))
        assertFalse(json.contains("relatertPartAktørId"))
        assertFalse(json.contains("AKTOR-1"))
        assertTrue(json.contains("saksnummer"))
    }
}

