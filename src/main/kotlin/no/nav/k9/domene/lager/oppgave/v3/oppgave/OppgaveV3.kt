package no.nav.k9.domene.lager.oppgave.v3.oppgave

import no.nav.k9.domene.lager.oppgave.v3.oppgavetype.Oppgavetype

class OppgaveV3(
    val id: String,
    val type: String,
    val status: String, //TODO: definere typer/enum
    val område: String,
    val felter: Set<OppgaveFeltverdi>
) {
    constructor(oppgaveDto: OppgaveDto, oppgavetype: Oppgavetype) : this(
        id = oppgaveDto.id,
        type = oppgaveDto.type,
        status = oppgaveDto.status,
        område = oppgaveDto.område,
        felter = oppgaveDto.feltverdier.map { oppgaveFeltverdiDto ->
            OppgaveFeltverdi(
                oppgavefeltId = oppgavetype.oppgavefelter.find {
                    it.feltDefinisjon.navn.equals(oppgaveFeltverdiDto.nøkkel)
                }?.id.takeIf { it != null } ?: throw IllegalStateException("Kunne ikke finne matchede oppgavefelt for oppgaveFeltverdi"),
                område = oppgaveDto.område, // TODO dette skal også på sikt kunne overstyres med område fra oppgaveFeltverdiDto
                nøkkel = oppgaveFeltverdiDto.nøkkel,
                verdi = oppgaveFeltverdiDto.verdi
            )
        }.toSet()
    )
}