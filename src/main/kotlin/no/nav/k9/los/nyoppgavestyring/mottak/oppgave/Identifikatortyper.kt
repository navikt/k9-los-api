package no.nav.k9.los.nyoppgavestyring.mottak.oppgave
sealed class OppgaveId(open val id : Long)

data class OppgaveV3Id(override val id : Long) : OppgaveId(id)
data class AktivOppgaveId(override val id : Long) : OppgaveId(id)