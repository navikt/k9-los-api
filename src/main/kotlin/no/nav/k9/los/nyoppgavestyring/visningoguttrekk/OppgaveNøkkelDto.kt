package no.nav.k9.los.nyoppgavestyring.visningoguttrekk

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

    fun erV1Oppgave(): Boolean {
        return oppgaveTypeEksternId == V1Oppgave
    }

    companion object {
        val V1Oppgave = "V1Oppgave"
        fun forV1Oppgave(eksternId: String): OppgaveNøkkelDto {
            return OppgaveNøkkelDto(
                oppgaveEksternId = eksternId,
                oppgaveTypeEksternId = V1Oppgave,
                områdeEksternId = V1Oppgave,
            )
        }
    }
}