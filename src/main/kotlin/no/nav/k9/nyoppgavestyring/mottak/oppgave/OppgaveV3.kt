package no.nav.k9.nyoppgavestyring.mottak.oppgave

import no.nav.k9.nyoppgavestyring.mottak.oppgavetype.Oppgavefelt
import no.nav.k9.nyoppgavestyring.mottak.oppgavetype.Oppgavetype
import java.time.LocalDateTime

class OppgaveV3(
    val id: Long? = null,
    val eksternId: String,
    val eksternVersjon: String,
    val oppgavetype: Oppgavetype,
    val status: String, //TODO: definere typer/enum
    val endretTidspunkt: LocalDateTime,
    val kildeområde: String,
    val felter: List<OppgaveFeltverdi>
) {
    constructor(oppgaveDto: OppgaveDto, oppgavetype: Oppgavetype) : this(
        eksternId = oppgaveDto.id,
        eksternVersjon = oppgaveDto.versjon,
        oppgavetype = oppgavetype,
        status = oppgaveDto.status,
        endretTidspunkt = oppgaveDto.endretTidspunkt,
        kildeområde = oppgaveDto.kildeområde,
        felter = lagFelter(oppgaveDto, oppgavetype)
    )

    fun hentVerdi(feltnavn: String): OppgaveFeltverdi? {
        val oppgavefelt = hentOppgavefelt(feltnavn)

        if (oppgavefelt.feltDefinisjon.listetype) {
            throw IllegalStateException("Kan ikke hente listetype som enkeltverdi")
        }

        val feltverdi = felter.find { feltverdi ->
            feltverdi.oppgavefelt.feltDefinisjon.eksternId.equals(feltnavn)
        } ?: if (oppgavefelt.påkrevd) throw IllegalStateException("Datafeil. Oppgave mangler påkrevd verdi") else null

        return feltverdi
    }

    fun hentListeverdi(feltnavn: String): List<OppgaveFeltverdi> {
        val oppgavefelt = hentOppgavefelt(feltnavn)

        if (!oppgavefelt.feltDefinisjon.listetype) {
            throw IllegalStateException("Kan ikke hente enkeltverdi som listetype")
        }

        val feltverdier = felter.filter { feltverdi ->
            feltverdi.oppgavefelt.feltDefinisjon.eksternId.equals(feltnavn)
        }

        if (feltverdier.isEmpty() && oppgavefelt.påkrevd) {
            throw IllegalStateException("Datafeil. Oppgave mangler påkrevd verdi")
        }

        return feltverdier
    }

    private fun hentOppgavefelt(feltnavn: String): Oppgavefelt {
        return oppgavetype.oppgavefelter.find { oppgavefelt ->
            oppgavefelt.feltDefinisjon.eksternId.equals(feltnavn)
        } ?: throw IllegalStateException ("Oppslag på oppgavefelt som ikke er definert for oppgavetypen")
    }

    companion object {
        private fun lagFelter(oppgaveDto: OppgaveDto, oppgavetype: Oppgavetype): List<OppgaveFeltverdi> {
            val oppgavefelter = mutableListOf<OppgaveFeltverdi>()

            oppgaveDto.feltverdier.forEach { oppgaveFeltverdiDto ->
                val oppgavefelt = oppgavetype.oppgavefelter.find {
                    it.feltDefinisjon.eksternId.equals(oppgaveFeltverdiDto.nøkkel)
                } ?: throw IllegalStateException("Kunne ikke finne matchede oppgavefelt for oppgaveFeltverdi: ${oppgaveFeltverdiDto.nøkkel}")

                if (oppgaveFeltverdiDto.verdi == null) {
                    if (oppgavefelt.påkrevd) {
                        if (oppgavefelt.feltDefinisjon.listetype) {
                            //skip. lagrer ikke verdier for tomme lister
                        } else {
                            throw IllegalArgumentException("Mangler obligatorisk feltverdi for ${oppgavefelt.feltDefinisjon.eksternId}")
                        }
                    }
                } else {
                    oppgavefelter.add(
                        OppgaveFeltverdi(
                            oppgavefelt = oppgavefelt,
                            verdi = oppgaveFeltverdiDto.verdi,
                        )
                    )
                }
            }
            return oppgavefelter
        }
    }
}