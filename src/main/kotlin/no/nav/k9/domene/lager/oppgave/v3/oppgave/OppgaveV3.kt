package no.nav.k9.domene.lager.oppgave.v3.oppgave

import no.nav.k9.domene.lager.oppgave.v3.oppgavetype.Oppgavefelt

class OppgaveV3(
    val id: String,
    val type: String,
    val status: String, //TODO: definere typer/enum
    val område: String,
    val felter: Set<OppgaveFeltverdi>
) {
    constructor(oppgaveDto: OppgaveDto, oppgavefelt: Set<Oppgavefelt>): this(
        id = oppgaveDto.id,
        type = oppgaveDto.type,
        status = oppgaveDto.status,
        område = oppgaveDto.område,
        felter = oppgaveDto.feltverdier.map { oppgaveFeltverdiDto ->
            OppgaveFeltverdi(
                oppgavefeltId =
               nøkkel = oppgaveFeltverdiDto.nøkkel,
               verdi = oppgaveFeltverdiDto.verdi
            )
        }.toSet()
    )
}