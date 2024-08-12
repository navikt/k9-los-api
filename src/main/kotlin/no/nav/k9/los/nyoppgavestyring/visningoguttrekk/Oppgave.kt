package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import no.nav.k9.los.spi.felter.HentVerdiInput
import java.time.LocalDateTime

data class Oppgave(
    val eksternId: String,
    val eksternVersjon: String,
    val reservasjonsnøkkel: String,
    val oppgavetype: Oppgavetype,
    val status: String, //TODO: definere typer/enum
    val endretTidspunkt: LocalDateTime,
    val kildeområde: String,
    val felter: List<Oppgavefelt>,
    val versjon: Int,
) {
    fun getOppgaveBehandlingsurl(): String? {
        var oppgavebehandlingsUrlTemplate: String = oppgavetype.oppgavebehandlingsUrlTemplate ?: return null
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
                feltverdi.område == område && feltverdi.eksternId == feltnavn
            }.map { it.verdi }
        }
    }

    fun hentListeverdi(feltnavn: String): List<String> {
        val oppgavefelt = hentOppgavefelt(feltnavn)

        if (oppgavefelt != null) {
            if (!oppgavefelt.listetype) {
                throw IllegalStateException("Kan ikke hente enkeltverdi av $feltnavn som listetype")
            }
        }

        return felter.filter { feltverdi ->
            feltverdi.eksternId == feltnavn
        }.map { it.verdi }
    }

    private fun hentOppgavefelt(feltnavn: String): Oppgavefelt? {
        return felter.find { oppgavefelt ->
            oppgavefelt.eksternId == feltnavn
        }
    }

    private fun hentOppgavefelt(område: String, feltnavn: String): Oppgavefelt? {
        return felter.find { oppgavefelt ->
            oppgavefelt.område == område && oppgavefelt.eksternId == feltnavn
        }
    }

    fun utledTransienteFelter(now: LocalDateTime): Oppgave {
        val utlededeVerdier: List<Oppgavefelt> = this.oppgavetype.oppgavefelter.flatMap { oppgavefelt ->
            oppgavefelt.feltDefinisjon.transientFeltutleder?.let { feltutleder ->
                feltutleder.hentVerdi(
                    HentVerdiInput(
                        now,
                        this,
                        oppgavefelt.feltDefinisjon.område.eksternId,
                        oppgavefelt.feltDefinisjon.eksternId
                    )
                ).map { verdi ->
                    Oppgavefelt(
                        eksternId = oppgavefelt.feltDefinisjon.eksternId,
                        område = oppgavefelt.feltDefinisjon.område.eksternId,
                        listetype = oppgavefelt.feltDefinisjon.listetype,
                        påkrevd = false,
                        verdi = verdi
                    )
                }
            } ?: listOf()
        }
        return copy(felter = felter.plus(utlededeVerdier))
    }

    fun fyllDefaultverdier(): Oppgave {
        val defaultverdier = oppgavetype.oppgavefelter
            .filter { oppgavefelt -> oppgavefelt.påkrevd }
            .mapNotNull { påkrevdFelt ->
                if (felter.find { it.eksternId == påkrevdFelt.feltDefinisjon.eksternId && !påkrevdFelt.feltDefinisjon.listetype } == null) {
                    Oppgavefelt(
                        eksternId = påkrevdFelt.feltDefinisjon.eksternId,
                        område = kildeområde,
                        listetype = false, //listetyper er aldri påkrevd
                        påkrevd = true,
                        verdi = påkrevdFelt.defaultverdi.toString()
                    )
                } else null
            }

        return copy(felter = felter.plus(defaultverdier))
    }
}