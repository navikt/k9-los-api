package no.nav.k9.los.nyoppgavestyring.query.dto.resultat

import no.nav.k9.los.nyoppgavestyring.query.db.EksternOppgaveId

data class OppgaveResultat(
    val id: EksternOppgaveId,
    val felter: List<Oppgavefeltverdi>
)