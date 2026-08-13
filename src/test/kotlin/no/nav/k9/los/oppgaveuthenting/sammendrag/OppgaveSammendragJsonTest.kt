package no.nav.k9.los.oppgaveuthenting.sammendrag

import assertk.assertThat
import assertk.assertions.containsExactlyInAnyOrder
import assertk.assertions.isEqualTo
import no.nav.k9.los.infrastruktur.utils.LosObjectMapper
import no.nav.k9.los.ko.dto.OppgaverFraKøDto
import no.nav.k9.los.oppgaveuthenting.OppgaveNøkkelDto
import no.nav.k9.los.søkeboks.SøkeresultatSammendrag
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

class OppgaveSammendragJsonTest {
    @Test
    fun `køkonvolutten har faste oppgavefelt og ingen dynamiske rader`() {
        val json = LosObjectMapper.instance.readTree(
            LosObjectMapper.instance.writeValueAsString(OppgaverFraKøDto(listOf(oppgave())))
        )

        assertThat(json.fieldNames().asSequence().toList()).containsExactlyInAnyOrder("oppgaver")
        assertThat(json["oppgaver"][0].fieldNames().asSequence().toList()).containsExactlyInAnyOrder(
            "oppgaveNøkkel", "reservasjonsnøkkel", "person", "ytelse", "behandlingstype", "saksnummer",
            "journalpostId", "fagsakÅr", "opprettetTidspunkt", "oppgavestatus", "behandlingsstatus",
            "oppgavebehandlingsUrl", "hastesak",
        )
    }

    @Test
    fun `søkekonvolutten beholder type og legger person per oppgave`() {
        val json = LosObjectMapper.instance.readTree(
            LosObjectMapper.instance.writeValueAsString(SøkeresultatSammendrag.MedResultat(listOf(oppgave())))
        )

        assertThat(json["type"].asText()).isEqualTo("MED_RESULTAT")
        assertThat(json["oppgaver"][0]["person"]["fnr"].asText()).isEqualTo("12345678901")
    }

    private fun oppgave() = OppgaveSammendragDto(
        oppgaveNøkkel = OppgaveNøkkelDto("oppgave-1", "k9sak", "K9"),
        reservasjonsnøkkel = "reservasjon-1",
        person = PersonSammendragDto("Ola Nordmann", "12345678901", "MANN", null),
        ytelse = KodeOgNavnDto("PSB", "Pleiepenger sykt barn"),
        behandlingstype = KodeOgNavnDto("BT-002", "Førstegangsbehandling"),
        saksnummer = "SAK-1",
        journalpostId = "123456789",
        fagsakÅr = 2026,
        opprettetTidspunkt = LocalDateTime.parse("2026-08-12T09:00:00"),
        oppgavestatus = KodeOgNavnDto("AAPEN", "Åpen"),
        behandlingsstatus = KodeOgNavnDto("UTRED", "Utredes"),
        oppgavebehandlingsUrl = null,
        hastesak = false,
    )
}
