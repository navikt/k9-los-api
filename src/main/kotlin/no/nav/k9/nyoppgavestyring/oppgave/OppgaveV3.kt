package no.nav.k9.nyoppgavestyring.oppgave

import no.nav.k9.nyoppgavestyring.oppgavetype.Oppgavetype
import java.lang.IllegalArgumentException

class OppgaveV3(
    val id: Long? = null,
    val eksternId: String,
    val eksternVersjon: String,
    val oppgavetype: Oppgavetype,
    val status: String, //TODO: definere typer/enum
    val kildeområde: String,
    val felter: List<OppgaveFeltverdi>
) {
    constructor(oppgaveDto: OppgaveDto, oppgavetype: Oppgavetype) : this(
        eksternId = oppgaveDto.id,
        eksternVersjon = oppgaveDto.versjon,
        oppgavetype = oppgavetype,
        status = oppgaveDto.status,
        kildeområde = oppgaveDto.kildeområde,
        felter = lagFelter(oppgaveDto, oppgavetype)
            /*
            OppgaveFeltverdi(

                //?: throw IllegalStateException("Kunne ikke finne matchede oppgavefelt for oppgaveFeltverdi"),
                //verdi = oppgaveFeltverdiDto.verdi
            )

        }*/
    )

    private fun lagFelter(oppgaveDto: OppgaveDto, oppgavetype: Oppgavetype): List<OppgaveFeltverdi> {
        val oppgavefelter = oppgaveDto.feltverdier.map { oppgaveFeltverdiDto ->
            val oppgavefelt = oppgavetype.oppgavefelter.find {
                it.feltDefinisjon.eksternId.equals(oppgaveFeltverdiDto.nøkkel)
            }
                ?: throw IllegalStateException("Kunne ikke finne matchede oppgavefelt for oppgaveFeltverdi: ${oppgaveFeltverdiDto.nøkkel}")

            if (oppgaveFeltverdiDto.verdi == null) {
                if (oppgavefelt.påkrevd) {
                    if (oppgavefelt.feltDefinisjon.listetype) {
                        //skip. lagrer ikke verdier for tomme lister
                    } else {
                        throw IllegalArgumentException("Mangler obligatorisk feltverdi for ${oppgavefelt.feltDefinisjon.eksternId}")
                    }
                } else {
                    //
                }
            } else {
                OppgaveFeltverdi(
                    oppgavefelt = oppgavefelt,
                    verdi = oppgaveFeltverdiDto.verdi,
                )
            }
        }

        return oppgavefelter
    }
}