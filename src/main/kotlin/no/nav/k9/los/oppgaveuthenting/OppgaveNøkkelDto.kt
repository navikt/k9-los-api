package no.nav.k9.los.oppgaveuthenting

import no.nav.k9.los.oppgavemottak.OppgaveDto

data class OppgaveNøkkelDto(
    val oppgaveEksternId: String,
    val oppgaveTypeEksternId: String,
    val områdeEksternId: String,
) {
    constructor(oppgave: Oppgave): this(
        oppgaveEksternId = oppgave.eksternId,
        oppgaveTypeEksternId = oppgave.oppgavetype.eksternId,
        områdeEksternId = oppgave.oppgavetype.område.eksternId
    )

    constructor(oppgaveDto: OppgaveDto): this(
        oppgaveEksternId = oppgaveDto.eksternId,
        oppgaveTypeEksternId = oppgaveDto.type,
        områdeEksternId = oppgaveDto.område
    )
}