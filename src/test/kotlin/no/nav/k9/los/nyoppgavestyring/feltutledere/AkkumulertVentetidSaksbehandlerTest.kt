package no.nav.k9.los.nyoppgavestyring.feltutledere

import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Feltdefinisjon
import no.nav.k9.los.nyoppgavestyring.mottak.omraade.Område
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveFeltverdi
import no.nav.k9.los.nyoppgavestyring.mottak.oppgave.OppgaveV3
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import java.time.Duration
import java.time.LocalDateTime

internal class AkkumulertVentetidSaksbehandlerTest {
    val akkumulertVentetidUtleder = AkkumulertVentetidSaksbehandler()

    @Test
    fun utledFørsteMelding() {
        assertNull(akkumulertVentetidUtleder.utled(lagOppgave(avventerSaksbehandler = true, LocalDateTime.now()), null))
        assertNull(
            akkumulertVentetidUtleder.utled(
                lagOppgave(avventerSaksbehandler = false, LocalDateTime.now()),
                null
            )
        )
    }

    @Test
    fun utledNesteOppgaveversjon() {
        val nå = LocalDateTime.now()
        val femMinuttersiden = nå.minusMinutes(5)

        val utledet = akkumulertVentetidUtleder.utled(
            innkommendeOppgave = lagOppgave(avventerSaksbehandler = true, nå),
            aktivOppgaveVersjon = lagOppgave(avventerSaksbehandler = true, femMinuttersiden)
        )

        assertEquals(Duration.ofMinutes(5), Duration.parse(utledet!!.verdi))
    }

    @Test
    fun utledOppgaveversjonMedAllereAkkumulert() {
        val nå = LocalDateTime.now()
        val femMinuttersiden = nå.minusMinutes(5)

        val aktivOppgave = lagOppgave(avventerSaksbehandler = true, femMinuttersiden, lagOppgavefeltverdi("akkumulertVentetidSaksbehandler", verdi = Duration.ofMinutes(10).toString()))


        val utledet = akkumulertVentetidUtleder.utled(
            innkommendeOppgave = lagOppgave(avventerSaksbehandler = true, nå),
            aktivOppgaveVersjon = aktivOppgave
        )

        assertEquals(Duration.ofMinutes(15), Duration.parse(utledet!!.verdi))
    }

    @Test
    fun `aktiv oppgave som ikke venter på saksbehandler skal ikke akkumulere ventetid`() {
        val nå = LocalDateTime.now()
        val femMinuttersiden = nå.minusMinutes(5)

        val aktivOppgave = lagOppgave(avventerSaksbehandler = false, femMinuttersiden, lagOppgavefeltverdi("akkumulertVentetidSaksbehandler", verdi = Duration.ofMinutes(10).toString()))


        val utledet = akkumulertVentetidUtleder.utled(
            innkommendeOppgave = lagOppgave(avventerSaksbehandler = true, nå),
            aktivOppgaveVersjon = aktivOppgave
        )

        assertEquals(Duration.ofMinutes(10), Duration.parse(utledet!!.verdi))
    }

    fun lagOppgave(avventerSaksbehandler: Boolean, endretTidspunkt: LocalDateTime): OppgaveV3 {
        return lagOppgave(avventerSaksbehandler, endretTidspunkt, null)
    }

    fun lagOppgave(
        avventerSaksbehandler: Boolean,
        endretTidspunkt: LocalDateTime,
        ekstraFeltverdi: OppgaveFeltverdi?
    ): OppgaveV3 {
        return OppgaveV3(
            eksternId = "123",
            eksternVersjon = "456",
            oppgavetype = lagOppgaveType(),
            status = "åpen",
            kildeområde = "junit",
            endretTidspunkt = endretTidspunkt,
            felter = ekstraFeltverdi?.let { //TODO: lekrere kotlinkode for dette?
                listOf(
                    lagOppgavefeltverdi("avventerSaksbehandler", avventerSaksbehandler.toString()),
                    ekstraFeltverdi
                )
            } ?: listOf(
                lagOppgavefeltverdi(
                    "avventerSaksbehandler",
                    avventerSaksbehandler.toString()
                )
            )
        )
    }

    private fun lagOppgaveType(): Oppgavetype {
        return Oppgavetype(
            eksternId = "123",
            område = Område(eksternId = "test"),
            definisjonskilde = "junit",
            oppgavefelter = setOf(
                lagOppgavefelt("avventerSaksbehandler"),
                lagOppgavefelt("akkumulertVentetidSaksbehandler")
            )
        )
    }

    fun lagOppgavefeltverdi(eksternId: String, verdi: String): OppgaveFeltverdi {
        return OppgaveFeltverdi(
            oppgavefelt = lagOppgavefelt(eksternId),
            verdi = verdi.toString()
        )

    }

    private fun lagOppgavefelt(eksternId: String) = Oppgavefelt(
        feltDefinisjon = Feltdefinisjon(
            eksternId = eksternId,
            område = Område(
                eksternId = "test"
            ),
            listetype = false,
            tolkesSom = "Boolean",
            visTilBruker = true
        ),
        visPåOppgave = true,
        påkrevd = false,
        defaultverdi = "defaultverdi"
    )
}