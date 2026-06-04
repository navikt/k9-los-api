package no.nav.k9.los.nyoppgavestyring.feltutlederforlagring

import no.nav.k9.los.nyoppgavestyring.oppgavedefinisjon.feltdefinisjon.Feltdefinisjon
import no.nav.k9.los.nyoppgavestyring.oppgavedefinisjon.feltdefinisjon.Synlighet
import no.nav.k9.los.nyoppgavestyring.oppgavedefinisjon.omraade.Område
import no.nav.k9.los.nyoppgavestyring.oppgavemottak.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.oppgavemottak.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.oppgavedefinisjon.oppgavetype.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.oppgavedefinisjon.oppgavetype.Oppgavetype
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class FerdigstiltTidspunktTest {
    
    @Test
    fun `utled returnerer null når oppgave ikke er lukket`() {
        val oppgave = lagOppgave(status = Oppgavestatus.AAPEN)
        assertNull(FerdigstiltTidspunkt.utled(oppgave, null))
    }

    @Test
    fun `utled returnerer ferdigstiltTidspunkt fra aktivOppgaveVersjon hvis den finnes`() {
        val tidspunkt = "2024-03-10T12:30:45"
        
        val aktivOppgaveVersjon = lagOppgave(
            status = Oppgavestatus.LUKKET,
            ekstraFeltverdi = OppgaveFeltverdi(
                oppgavefelt = Oppgavefelt(
                    feltDefinisjon = Feltdefinisjon(
                        eksternId = "ferdigstiltTidspunkt",
                        område = Område(eksternId = "test"),
                        visningsnavn = "Test",
                        beskrivelse = null,
                        listetype = false,
                        tolkesSom = "string",

                        synlighet = Synlighet.OVER_STREKEN,
                        kodeverkreferanse = null,
                        transientFeltutleder = null,
                    ),
                    visPåOppgave = true,
                    påkrevd = false,
                    defaultverdi = null
                ),
                verdi = tidspunkt,
                verdiBigInt = null
            )
        )
        
        val oppgave = lagOppgave(status = Oppgavestatus.LUKKET)
        
        val resultat = FerdigstiltTidspunkt.utled(oppgave, aktivOppgaveVersjon)
        
        assertEquals(tidspunkt, resultat?.verdi)
    }

    @Test
    fun `utled bruker oppgavens endretTidspunkt hvis aktivOppgaveVersjon mangler ferdigstiltTidspunkt`() {
        val nå = LocalDateTime.now()
        val oppgave = lagOppgave(status = Oppgavestatus.LUKKET, endretTidspunkt = nå)
        
        val resultat = FerdigstiltTidspunkt.utled(oppgave, null)
        
        assertEquals(nå.toString(), resultat?.verdi)
    }

    private fun lagOppgave(
        status: Oppgavestatus, 
        ekstraFeltverdi: OppgaveFeltverdi? = null,
        endretTidspunkt: LocalDateTime = LocalDateTime.now()
    ): OppgaveV3 {
        return OppgaveV3(
            eksternId = "123",
            eksternVersjon = "456",
            oppgavetype = lagOppgaveType(),
            status = status,
            kildeområde = "junit",
            endretTidspunkt = endretTidspunkt,
            reservasjonsnøkkel = "reservasjonsnøkkel",
            felter = ekstraFeltverdi?.let { listOf(it) } ?: emptyList(),
            aktiv = true
        )
    }

    private fun lagOppgaveType(): Oppgavetype {
        return Oppgavetype(
            eksternId = "123",
            område = Område(eksternId = "test"),
            definisjonskilde = "junit",
            oppgavebehandlingsUrlTemplate = "\${baseUrl}/fagsak/\${K9.saksnummer}/behandling/\${K9.behandlingUuid}",
            oppgavefelter = setOf(
                Oppgavefelt(
                    feltDefinisjon = Feltdefinisjon(
                        eksternId = "ferdigstiltTidspunkt",
                        område = Område(eksternId = "test"),
                        visningsnavn = "Test",
                        beskrivelse = null,
                        listetype = false,
                        tolkesSom = "string",

                        synlighet = Synlighet.OVER_STREKEN,
                        kodeverkreferanse = null,
                        transientFeltutleder = null,
                    ),
                    visPåOppgave = true,
                    påkrevd = false,
                    defaultverdi = null
                )
            )
        )
    }

}