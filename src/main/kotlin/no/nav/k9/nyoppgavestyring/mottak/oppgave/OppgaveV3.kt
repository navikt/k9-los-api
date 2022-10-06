package no.nav.k9.nyoppgavestyring.mottak.oppgave

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
                            val sanertOppgaveDto =
                                oppgaveDto.copy(feltverdier = oppgaveDto.feltverdier.filterNot { it.nøkkel == "aktorId" })
                            throw IllegalArgumentException("Mangler obligatorisk feltverdi for ${oppgavefelt.feltDefinisjon.eksternId}. \n" +
                                    jacksonObjectMapper().registerModule(JavaTimeModule()).writeValueAsString(sanertOppgaveDto)
                            )
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

    fun hentVerdi(feltnavn: String): String? {
        val oppgavefelt = hentOppgavefelt(feltnavn)

        if (oppgavefelt?.oppgavefelt?.feltDefinisjon?.listetype == true) {
            throw IllegalStateException("Kan ikke hente listetype av $feltnavn som enkeltverdi")
        }

        return oppgavefelt?.verdi
    }

    fun hentListeverdi(feltnavn: String): List<String> {
        val oppgavefelt = hentOppgavefelt(feltnavn)

        if (oppgavefelt != null) {
            if (!oppgavefelt.oppgavefelt.feltDefinisjon.listetype) {
                throw IllegalStateException("Kan ikke hente enkeltverdi av $feltnavn som listetype")
            }
        }

        return felter.filter { feltverdi ->
            feltverdi.oppgavefelt.feltDefinisjon.eksternId == feltnavn
        }.map { it.verdi }
    }

    private fun hentOppgavefelt(feltnavn: String): OppgaveFeltverdi? {
        return felter.find { oppgavefelter ->
            oppgavefelter.oppgavefelt.feltDefinisjon.eksternId == feltnavn
        }
    }
}