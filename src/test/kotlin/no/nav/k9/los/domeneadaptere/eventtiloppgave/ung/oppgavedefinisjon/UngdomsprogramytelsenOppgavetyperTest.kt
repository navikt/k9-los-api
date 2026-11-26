package no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.oppgavedefinisjon

import assertk.assertThat
import assertk.assertions.isEqualTo
import no.nav.k9.los.domeneadaptere.eventtiloppgave.ung.kodeverk.UngOppgavetypenavn
import org.junit.jupiter.api.Test

class UngdomsprogramytelsenOppgavetyperTest {
    @Test
    fun `ordinær og klage åpnes i aktivitetspenger-fagsystemet`() {
        val frontendUrl = "https://aktivitetspenger.intern.nav.no/ung/web"
        val oppgavetyper = UngdomsprogramytelsenOppgavetyper.tilDto(frontendUrl).oppgavetyper

        assertThat(oppgavetyper.associate { it.id to it.oppgavebehandlingsUrlTemplate }).isEqualTo(
            mapOf(
                UngOppgavetypenavn.UNGDOMSPROGRAMYTELSENORDINÆR.kode to "$frontendUrl/fagsak/{saksnummer}/",
                UngOppgavetypenavn.UNGDOMSPROGRAMYTELSENKLAGE.kode to "$frontendUrl/fagsak/{saksnummer}/",
            )
        )
    }
}
