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
    fun hentVerdi(feltnavn: String): String? {
        val oppgavefelt = hentOppgavefelt(feltnavn)

        if (oppgavefelt?.listetype == true) {
            throw IllegalStateException("Kan ikke hente listetype som enkeltverdi")
        }

        return oppgavefelt?.verdi
    }

    fun hentListeverdi(feltnavn: String): List<String> {
        val oppgavefelt = hentOppgavefelt(feltnavn)

        if (oppgavefelt?.listetype != true) {
            throw IllegalStateException("Kan ikke hente enkeltverdi som listetype")
        }

        return felter.filter { feltverdi ->
            feltverdi.eksternId.equals(feltnavn)
        }.map { it.verdi }
    }

    private fun hentOppgavefelt(feltnavn: String): Oppgavefelt? {
        return felter.find { oppgavefelt ->
            oppgavefelt.eksternId.equals(feltnavn)
        }
    }
}