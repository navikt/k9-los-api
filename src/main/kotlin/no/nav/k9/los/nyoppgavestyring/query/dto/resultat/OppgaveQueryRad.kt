package no.nav.k9.los.nyoppgavestyring.query.dto.resultat

import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId
import no.nav.k9.los.nyoppgavestyring.query.db.OppgaveId

data class OppgaveQueryRad(
    val oppgaveId: OppgaveId? = null,
    val eksternOppgaveId: EksternOppgaveId? = null,
    val feltverdier: List<Oppgavefeltverdi> = emptyList(),
    val aggregeringer: List<Aggregertverdi> = emptyList(),
)
