package no.nav.k9.nyoppgavestyring.visningoguttrekk

import java.time.LocalDateTime

class Oppgave(
    val eksternId: String,
    val eksternVersjon: String,
    val oppgavetype: String,
    val status: String, //TODO: definere typer/enum
    val endretTidspunkt: LocalDateTime,
    val kildeområde: String,
    val felter: List<Oppgavefelt>
) {
    fun hentVerdi(feltnavn: String): Oppgavefelt? {
        val oppgavefelt = hentOppgavefelt(feltnavn)

        if (oppgavefelt == null) {
            //TODO() er overflødig å sjekke noe som har passert validering i mottak?
        }

        if (oppgavefelt?.listetype == true) {
            throw IllegalStateException("Kan ikke hente listetype som enkeltverdi")
        }

        return oppgavefelt
    }

    fun hentListeverdi(feltnavn: String): List<Oppgavefelt> {
        val oppgavefelt = hentOppgavefelt(feltnavn)

        if (oppgavefelt?.listetype != true) {
            throw IllegalStateException("Kan ikke hente enkeltverdi som listetype")
        }

        val feltverdier = felter.filter { feltverdi ->
            feltverdi.eksternId.equals(feltnavn)
        }

        return feltverdier
    }

    private fun hentOppgavefelt(feltnavn: String): Oppgavefelt? {
        return felter.find { oppgavefelt ->
            oppgavefelt.eksternId.equals(feltnavn)
        }
    }
}