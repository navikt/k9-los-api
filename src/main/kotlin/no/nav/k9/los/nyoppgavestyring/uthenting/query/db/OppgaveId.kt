package no.nav.k9.los.nyoppgavestyring.uthenting.query.db

sealed class OppgaveId

data class OppgaveV3Id(val id: Long) : OppgaveId()
data class PartisjonertOppgaveId(val id: Long) : OppgaveId()