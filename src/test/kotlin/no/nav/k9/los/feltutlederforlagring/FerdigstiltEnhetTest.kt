package no.nav.k9.los.feltutlederforlagring

import io.mockk.every
import io.mockk.mockk
import no.nav.k9.los.saksbehandleradmin.Saksbehandler
import no.nav.k9.los.saksbehandleradmin.SaksbehandlerRepository
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Feltdefinisjon
import no.nav.k9.los.oppgavedefinisjon.feltdefinisjon.Synlighet
import no.nav.k9.los.oppgavedefinisjon.omraade.Område
import no.nav.k9.los.oppgavemottak.OppgaveFeltverdi
import no.nav.k9.los.oppgavemottak.OppgaveV3
import no.nav.k9.los.oppgavedefinisjon.Oppgavestatus
import no.nav.k9.los.oppgavemottak.feltutlederforlagring.FerdigstiltEnhet
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.Oppgavefelt
import no.nav.k9.los.oppgavedefinisjon.oppgavetype.Oppgavetype
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
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
        } returns Saksbehandler(
            id = 0, navident = saksbehandlerId, navn = "Navn Navnesen", epost = "", enhet = forventetEnhet,
        )

        val oppgave = lagOppgave(
            status = Oppgavestatus.LUKKET,
            ekstraFeltverdi = lagOppgavefeltverdi("ansvarligSaksbehandler", saksbehandlerId)
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
        } returns Saksbehandler(id = 0, navident = saksbehandlerId, navn = "Navn Navnesen", epost = "", enhet = null)

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
}