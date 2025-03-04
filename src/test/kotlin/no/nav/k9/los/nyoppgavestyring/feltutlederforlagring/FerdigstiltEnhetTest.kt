package no.nav.k9.los.nyoppgavestyring.feltutlederforlagring

import io.mockk.every
import io.mockk.mockk
import no.nav.k9.los.domene.modell.Saksbehandler
import no.nav.k9.los.domene.repository.SaksbehandlerRepository
import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.Oppgavestatus
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class FerdigstiltEnhetTest {
    private val saksbehandlerRepository = mockk<SaksbehandlerRepository>()
    private val ferdigstiltEnhetUtleder = FerdigstiltEnhet(saksbehandlerRepository)

    @Test
    fun `utled returnerer null når oppgave ikke er lukket`() {
        val oppgave = lagOppgave(status = Oppgavestatus.AAPEN)
        assertNull(ferdigstiltEnhetUtleder.utled(oppgave, null))
    }

    @Test
    fun `utled returnerer ferdigstiltEnhet fra aktivOppgaveVersjon hvis den finnes`() {
        val aktivOppgaveVersjon = lagOppgave(
            status = Oppgavestatus.LUKKET, 
            ekstraFeltverdi = lagOppgavefeltverdi("ferdigstiltEnhet", "1234")
        )
        
        val oppgave = lagOppgave(status = Oppgavestatus.LUKKET)
        
        val resultat = ferdigstiltEnhetUtleder.utled(oppgave, aktivOppgaveVersjon)
        
        assertEquals("1234", resultat?.verdi)
    }

    @Test
    fun `utled finner saksbehandlers enhet fra ansvarligSaksbehandler`() {
        val saksbehandlerId = "Z12345"
        val forventetEnhet = "4567"
        
        every { 
            saksbehandlerRepository.finnSaksbehandlerMedIdentEkskluderKode6(saksbehandlerId) 
        } returns Saksbehandler(saksbehandlerId, "Navn Navnesen", forventetEnhet)
        
        val oppgave = lagOppgave(
            status = Oppgavestatus.LUKKET,
            ekstraFeltverdi = lagOppgavefeltverdi("ansvarligSaksbehandler", saksbehandlerId)
        )
        
        val resultat = ferdigstiltEnhetUtleder.utled(oppgave, null)
        
        assertEquals(forventetEnhet, resultat?.verdi)
    }

    @Test
    fun `utled finner saksbehandlers enhet fra ansvarligSaksbehandlerForToTrinn hvis ansvarligSaksbehandler ikke finnes`() {
        val saksbehandlerId = "Z67890"
        val forventetEnhet = "7890"
        
        every { 
            saksbehandlerRepository.finnSaksbehandlerMedIdentEkskluderKode6(saksbehandlerId) 
        } returns Saksbehandler(saksbehandlerId, "Beslutter Navnesen", forventetEnhet)
        
        val oppgave = lagOppgave(
            status = Oppgavestatus.LUKKET,
            ekstraFeltverdi = lagOppgavefeltverdi("ansvarligSaksbehandlerForToTrinn", saksbehandlerId)
        )
        
        val resultat = ferdigstiltEnhetUtleder.utled(oppgave, null)
        
        assertEquals(forventetEnhet, resultat?.verdi)
    }

    @Test
    fun `utled returnerer null hvis hverken aktivOppgaveVersjon eller saksbehandler finnes`() {
        every { 
            saksbehandlerRepository.finnSaksbehandlerMedIdentEkskluderKode6(any()) 
        } returns null
        
        val oppgave = lagOppgave(status = Oppgavestatus.LUKKET)
        
        assertNull(ferdigstiltEnhetUtleder.utled(oppgave, null))
    }

    @Test
    fun `utled returnerer null hvis saksbehandler finnes men enhet er null`() {
        val saksbehandlerId = "Z12345"
        
        every { 
            saksbehandlerRepository.finnSaksbehandlerMedIdentEkskluderKode6(saksbehandlerId) 
        } returns Saksbehandler(saksbehandlerId, "Navn Navnesen", null)
        
        val oppgave = lagOppgave(
            status = Oppgavestatus.LUKKET,
            ekstraFeltverdi = lagOppgavefeltverdi("ansvarligSaksbehandler", saksbehandlerId)
        )
        
        assertNull(ferdigstiltEnhetUtleder.utled(oppgave, null))
    }

    private fun lagOppgave(status: Oppgavestatus, ekstraFeltverdi: OppgaveFeltverdi? = null): OppgaveV3 {
        return OppgaveV3(
            eksternId = "123",
            eksternVersjon = "456",
            oppgavetype = lagOppgaveType(),
            status = status,
            kildeområde = "junit",
            endretTidspunkt = LocalDateTime.now(),
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
            oppgavebehandlingsUrlTemplate = "\${baseUrl}/fagsak/\${K9.saksnummer}",
            oppgavefelter = setOf(
                lagOppgavefelt("ferdigstiltEnhet"),
                lagOppgavefelt("ansvarligSaksbehandler"),
                lagOppgavefelt("ansvarligSaksbehandlerForToTrinn")
            )
        )
    }

    private fun lagOppgavefeltverdi(eksternId: String, verdi: String): OppgaveFeltverdi {
        return OppgaveFeltverdi(
            oppgavefelt = lagOppgavefelt(eksternId),
            verdi = verdi,
            verdiBigInt = null
        )
    }

    private fun lagOppgavefelt(eksternId: String) = Oppgavefelt(
        feltDefinisjon = Feltdefinisjon(
            eksternId = eksternId,
            område = Område(
                eksternId = "test"
            ),
            visningsnavn = "Test",
            listetype = false,
            tolkesSom = "string",
            visTilBruker = true,
            kokriterie = true,
            kodeverkreferanse = null,
            transientFeltutleder = null,
        ),
        visPåOppgave = true,
        påkrevd = false,
        defaultverdi = null
    )
}