package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import java.time.LocalDateTime

data class Oppgave(
    val eksternId: String,
    val eksternVersjon: String,
    val reservasjonsnøkkel: String,
    val oppgavetype: Oppgavetype,
    val status: String, //TODO: definere typer/enum
    val endretTidspunkt: LocalDateTime,
    val kildeområde: String,
    val felter: List<Oppgavefelt>
) {
    fun getOppgaveBehandlingsurl(): String {
        var oppgavebehandlingsUrlTemplate = oppgavetype.oppgavebehandlingsUrlTemplate
        val matcher = "\\{(.+?)\\}".toRegex()
        val matches = matcher.findAll(oppgavebehandlingsUrlTemplate, 0)
        matches.forEach { match ->
            val split = match.groupValues[1].split('.')
            if (split.size == 2) { //Det er frivillig å oppgi område. Brukes om man vil hente felt som hører til et annet område enn oppgaven
                val område = split[0]
                val feltnavn = split[1]
                val oppgavefelt = felter.find { oppgavefelt ->
                    oppgavefelt.område == område && oppgavefelt.eksternId == feltnavn
                }
                    ?: throw IllegalStateException("Finner ikke omsøkt oppgavefelt $feltnavn på oppgavetype ${oppgavetype.eksternId}")
                oppgavebehandlingsUrlTemplate = oppgavebehandlingsUrlTemplate.replace(match.value, oppgavefelt.verdi)
            } else if (split.size == 1) {
                val feltnavn = split[0]
                val oppgavefelt = felter.find { oppgavefelt ->
                    oppgavefelt.eksternId == feltnavn
                }
                    ?: throw IllegalStateException("Finner ikke omsøkt oppgavefelt $feltnavn på oppgavetype ${oppgavetype.eksternId}")
                oppgavebehandlingsUrlTemplate = oppgavebehandlingsUrlTemplate.replace(match.value, oppgavefelt.verdi)
            } else {
                throw IllegalStateException("Ugyldig format på feltanvisning i urlTemplate: ${match.value}. Format er {feltnavn} eller {område.feltnavn}")
            }
        }
        return oppgavebehandlingsUrlTemplate
    }

    fun hentVerdi(feltnavn: String): String? {
        val oppgavefelt = hentOppgavefelt(feltnavn)

        if (oppgavefelt?.listetype == true) {
            throw IllegalStateException("Kan ikke hente listetype av $feltnavn som enkeltverdi")
        }

        return oppgavefelt?.verdi
    }

    fun hentVerdi(område: String, feltnavn: String): String? {
        val oppgavefelt = hentOppgavefelt(område, feltnavn)

        if (oppgavefelt?.listetype == true) {
            throw IllegalStateException("Kan ikke hente listetype av $feltnavn som enkeltverdi")
        }

        return oppgavefelt?.verdi
    }

    fun hentVerdiEllerListe(område: String, feltnavn: String): Any? {
        val oppgavefelt = hentOppgavefelt(område, feltnavn)
        if (oppgavefelt == null) {
            return null
        }
        if (!oppgavefelt.listetype) {
            return oppgavefelt.verdi
        } else {
            return felter.filter { feltverdi ->
                feltverdi.område.equals(område) && feltverdi.eksternId.equals(feltnavn)
            }.map { it.verdi }
        }
    }

    fun hentListeverdi(område: String, feltnavn: String): List<String> {
        val oppgavefelt = hentOppgavefelt(område, feltnavn)
        if (oppgavefelt == null) {
            return listOf()
        }
        if (!oppgavefelt.listetype) {
            throw IllegalStateException("Kan ikke hente enkeltverdi av $feltnavn som listetype")
        }

        return felter.filter { feltverdi ->
            feltverdi.område.equals(område) && feltverdi.eksternId.equals(feltnavn)
        }.map { it.verdi }
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

    private fun hentOppgavefelt(område: String, feltnavn: String): Oppgavefelt? {
        return felter.find { oppgavefelt ->
            oppgavefelt.område.equals(område) && oppgavefelt.eksternId.equals(feltnavn)
        }
    }
}