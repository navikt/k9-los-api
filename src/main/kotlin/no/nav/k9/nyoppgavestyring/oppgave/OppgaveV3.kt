package no.nav.k9.nyoppgavestyring.oppgave

import no.nav.k9.nyoppgavestyring.oppgavetype.Oppgavetype

class OppgaveV3(
    val id: Long? = null,
    val eksternId: String,
    val oppgavetype: Oppgavetype,
    val status: String, //TODO: definere typer/enum
    val kildeområde: String,
    val felter: Set<OppgaveFeltverdi>
) {
    constructor(oppgaveDto: OppgaveDto, oppgavetype: Oppgavetype) : this(
        eksternId = oppgaveDto.id,
        oppgavetype = oppgavetype,
        status = oppgaveDto.status,
        kildeområde = oppgaveDto.kildeområde,
        felter = oppgaveDto.feltverdier.map { oppgaveFeltverdiDto ->
            OppgaveFeltverdi(
                oppgavefelt = oppgavetype.oppgavefelter.find {
                    it.feltDefinisjon.eksternId.equals(oppgaveFeltverdiDto.nøkkel)
                }.takeIf { it != null } ?: throw IllegalStateException("Kunne ikke finne matchede oppgavefelt for oppgaveFeltverdi"),
                verdi = oppgaveFeltverdiDto.verdi
            )
        }.toSet()
    )
}