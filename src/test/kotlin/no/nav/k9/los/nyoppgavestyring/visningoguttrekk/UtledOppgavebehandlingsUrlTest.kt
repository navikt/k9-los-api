package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import no.nav.k9.los.AbstractK9LosIntegrationTest
import no.nav.k9.los.nyoppgavestyring.datainnlasting.omraade.Område
import no.nav.k9.los.nyoppgavestyring.datainnlasting.oppgavetype.Oppgavetype
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import kotlin.test.assertEquals

//TODO DRY-forbedring
class UtledOppgavebehandlingsUrlTest : AbstractK9LosIntegrationTest(){
    @Test
    fun `mapUrlTemplateHappycase`() {
        val oppgavetype = Oppgavetype(
            eksternId = "test123",
            område = Område(eksternId = "K9"),
            definisjonskilde = "K9",
            oppgavebehandlingsUrlTemplate = "http://localhost:9000/fagsak/{K9.saksnummer}/behandling/{K9.behandlingUuid}?fakta=default&punkt=default",
            oppgavefelter = emptySet(),
        )
        val oppgavefelter = listOf(
            Oppgavefelt(
                eksternId = "saksnummer",
                område = "K9",
                listetype = false,
                påkrevd = false,
                verdi = "ABC123"
            ),
            Oppgavefelt(
                eksternId = "behandlingUuid",
                område = "K9",
                listetype = false,
                påkrevd = false,
                verdi = "beh456"
            ),
        )
        val oppgave = Oppgave(
            eksternId = "abc",
            eksternVersjon = "123",
            reservasjonsnøkkel = "test",
            oppgavetype = oppgavetype,
            status = "test",
            endretTidspunkt = LocalDateTime.now(),
            kildeområde = "K9",
            felter = oppgavefelter,
            versjon = 1
        )
        val url = oppgave.getOppgaveBehandlingsurl()
        assertEquals("http://localhost:9000/fagsak/ABC123/behandling/beh456?fakta=default&punkt=default", url)
    }

    @Test
    fun `mapUrlUtenOmrådeanvisning`() {
        val oppgavetype = Oppgavetype(
            eksternId = "test123",
            område = Område(eksternId = "K9"),
            definisjonskilde = "K9",
            oppgavebehandlingsUrlTemplate = "http://localhost:9000/fagsak/{saksnummer}/behandling/{behandlingUuid}?fakta=default&punkt=default",
            oppgavefelter = emptySet(),
        )
        val oppgavefelter = listOf(
            Oppgavefelt(
                eksternId = "saksnummer",
                område = "K9",
                listetype = false,
                påkrevd = false,
                verdi = "ABC123"
            ),
            Oppgavefelt(
                eksternId = "behandlingUuid",
                område = "K9",
                listetype = false,
                påkrevd = false,
                verdi = "beh456"
            ),
        )
        val oppgave = Oppgave(
            eksternId = "abc",
            eksternVersjon = "123",
            reservasjonsnøkkel = "test",
            oppgavetype = oppgavetype,
            status = "test",
            endretTidspunkt = LocalDateTime.now(),
            kildeområde = "K9",
            felter = oppgavefelter,
            versjon = 1
        )
        val url = oppgave.getOppgaveBehandlingsurl()
        assertEquals("http://localhost:9000/fagsak/ABC123/behandling/beh456?fakta=default&punkt=default", url)
    }

    @Test
    fun `mapUrlManglerOppgavefelt`() {
        val oppgavetype = Oppgavetype(
            eksternId = "test123",
            område = Område(eksternId = "K9"),
            definisjonskilde = "K9",
            oppgavebehandlingsUrlTemplate = "http://localhost:9000/fagsak/{saksnummer}/behandling/{behandlingUuid}?fakta=default&punkt=default",
            oppgavefelter = emptySet(),
        )
        val oppgavefelter = listOf(
            Oppgavefelt(
                eksternId = "saksnummer",
                område = "K9",
                listetype = false,
                påkrevd = false,
                verdi = "ABC123"
            ),
            Oppgavefelt(
                eksternId = "løsbartAksjonspunkt",
                område = "K9",
                listetype = false,
                påkrevd = false,
                verdi = "9001"
            ),
        )
        val oppgave = Oppgave(
            eksternId = "abc",
            eksternVersjon = "123",
            reservasjonsnøkkel = "test",
            oppgavetype = oppgavetype,
            status = "test",
            endretTidspunkt = LocalDateTime.now(),
            kildeområde = "K9",
            felter = oppgavefelter,
            versjon = 1
        )
        assertThrows<IllegalStateException> {
            oppgave.getOppgaveBehandlingsurl()
        }
    }
}