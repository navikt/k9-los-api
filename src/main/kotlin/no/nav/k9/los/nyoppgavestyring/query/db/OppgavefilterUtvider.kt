package no.nav.k9.los.nyoppgavestyring.query.db

import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter

object OppgavefilterUtvider {
    fun utvid(oppgavefiltere: List<Oppgavefilter>): List<Oppgavefilter> {
        return oppgavefiltere
            .apply { OppgavefilterDatoTypeUtvider.utvid(this) }
            .apply { OppgavefilterOperatorUtvider.utvid(this) }
    }
}