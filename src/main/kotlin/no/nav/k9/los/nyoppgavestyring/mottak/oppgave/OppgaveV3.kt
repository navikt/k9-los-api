package no.nav.k9.los.nyoppgavestyring.mottak.oppgave

import no.nav.k9.los.nyoppgavestyring.mottak.feltdefinisjon.Datatype
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt
import no.nav.k9.los.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import java.time.LocalDateTime

class OppgaveV3(
    val id: OppgaveId? = null,
    val eksternId: String,
    val eksternVersjon: String,
    val oppgavetype: Oppgavetype,
    val status: Oppgavestatus,
    val endretTidspunkt: LocalDateTime,
    val kildeområde: String,
    val reservasjonsnøkkel: String,
    val aktiv: Boolean,
    val felter: List<OppgaveFeltverdi>
) {
    constructor(oppgaveDto: OppgaveDto, oppgavetype: Oppgavetype) : this(
        eksternId = oppgaveDto.eksternId,
        eksternVersjon = oppgaveDto.eksternVersjon,
        oppgavetype = oppgavetype,
        status = Oppgavestatus.valueOf(oppgaveDto.status),
        endretTidspunkt = oppgaveDto.endretTidspunkt,
        kildeområde = oppgaveDto.kildeområde,
        reservasjonsnøkkel = oppgaveDto.reservasjonsnøkkel,
        aktiv = true,
        felter = lagFelter(oppgaveDto, oppgavetype)
    )

    constructor(oppgave: OppgaveV3, oppgavefelter: List<OppgaveFeltverdi>) : this(
        id = oppgave.id,
        eksternId = oppgave.eksternId,
        eksternVersjon = oppgave.eksternVersjon,
        oppgavetype = oppgave.oppgavetype,
        status = oppgave.status,
        endretTidspunkt = oppgave.endretTidspunkt,
        kildeområde = oppgave.kildeområde,
        reservasjonsnøkkel = oppgave.reservasjonsnøkkel,
        aktiv = oppgave.aktiv,
        felter = oppgavefelter
    )

    companion object {
        private fun lagFelter(oppgaveDto: OppgaveDto, oppgavetype: Oppgavetype): List<OppgaveFeltverdi> {
            val oppgavefelter = mutableListOf<OppgaveFeltverdi>()

            oppgaveDto.feltverdier.forEach { oppgaveFeltverdiDto ->
                val oppgavefelt = oppgavetype.oppgavefelter.find {
                    it.feltDefinisjon.eksternId == oppgaveFeltverdiDto.nøkkel
                } ?: throw IllegalArgumentException("Kunne ikke finne matchede oppgavefelt for oppgaveFeltverdi: ${oppgaveFeltverdiDto.nøkkel}")

                if (oppgaveFeltverdiDto.verdi == null) {
                    if (oppgavefelt.påkrevd) {
                        if (oppgavefelt.feltDefinisjon.listetype) {
                            //skip. lagrer ikke verdier for tomme lister
                        } else {
                            throw IllegalArgumentException("Mangler obligatorisk feltverdi for ${oppgavefelt.feltDefinisjon.eksternId}. Oppgavens eksternId: ${oppgaveDto.eksternId}\n")
                        }
                    }
                } else {
                    oppgavefelter.add(
                        OppgaveFeltverdi(
                            oppgavefelt = oppgavefelt,
                            verdi = oppgaveFeltverdiDto.verdi,
                            verdiBigInt = if (oppgavefelt.feltDefinisjon.tolkesSom == Datatype.INTEGER.kode) oppgaveFeltverdiDto.verdi.toLong() else null,
                        )
                    )
                }
            }
            return oppgavefelter
        }


    }

    fun hentFelt(feltEksternId: String) : Oppgavefelt {
        return oppgavetype.oppgavefelter.first { it.feltDefinisjon.eksternId == feltEksternId }
    }

    fun hentVerdi(feltnavn: String): String? {
        val oppgavefelt = hentOppgavefeltverdi(feltnavn)

        if (oppgavefelt?.oppgavefelt?.feltDefinisjon?.listetype == true) {
            throw IllegalStateException("Kan ikke hente listetype av $feltnavn som enkeltverdi")
        }

        return oppgavefelt?.verdi
    }

    fun hentListeverdi(feltnavn: String): List<String> {
        val oppgavefelt = hentOppgavefeltverdi(feltnavn)

        if (oppgavefelt != null) {
            if (!oppgavefelt.oppgavefelt.feltDefinisjon.listetype) {
                throw IllegalStateException("Kan ikke hente enkeltverdi av $feltnavn som listetype")
            }
        }

        return felter.filter { feltverdi ->
            feltverdi.oppgavefelt.feltDefinisjon.eksternId == feltnavn
        }.map { it.verdi }
    }

    fun hentOppgavefeltverdi(feltnavn: String): OppgaveFeltverdi? {
        return felter.find { oppgavefelter ->
            oppgavefelter.oppgavefelt.feltDefinisjon.eksternId == feltnavn
        }
    }

    fun valider() {
        oppgavetype.oppgavefelter
            .filter { it.påkrevd && !it.feltDefinisjon.listetype }
            .forEach { obligatoriskFelt ->
                felter.find {
                    it.oppgavefelt == obligatoriskFelt
                } ?: throw IllegalArgumentException("Oppgaven mangler obligatorisk felt " + obligatoriskFelt.feltDefinisjon.eksternId)
            }
    }
}