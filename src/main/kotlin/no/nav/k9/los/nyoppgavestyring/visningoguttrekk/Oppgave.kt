package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import java.time.LocalDateTime

data class Oppgave(
    val eksternId: String,
    val eksternVersjon: String,
    val oppgavetypeId: Long,
    val status: String, //TODO: definere typer/enum
    val endretTidspunkt: LocalDateTime,
    val kildeområde: String,
    val felter: List<Oppgavefelt>
) {
    fun hentVerdi(feltnavn: String): String? {
        val oppgavefelt = hentOppgavefelt(feltnavn)

        if (oppgavefelt?.listetype == true) {
            throw IllegalStateException("Kan ikke hente listetype av $feltnavn som enkeltverdi")
        }

        return oppgavefelt?.verdi
    }

    fun hentListeverdi(feltnavn: String): List<String> {
        val oppgavefelt = hentOppgavefelt(feltnavn)

        if (oppgavefelt != null) {
            if (!oppgavefelt.listetype) {
                throw IllegalStateException("Kan ikke hente enkeltverdi av $feltnavn som listetype")
            }
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