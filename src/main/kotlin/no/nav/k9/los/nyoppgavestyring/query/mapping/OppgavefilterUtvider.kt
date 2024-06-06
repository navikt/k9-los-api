package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter

object OppgavefilterUtvider {
    fun utvid(oppgavefiltere: List<Oppgavefilter>): List<Oppgavefilter> {
        return oppgavefiltere
            .let { OppgavefilterOperatorUtvider.utvid(it) }
            .let { OppgavefilterDatoTypeUtvider.utvid(it) }
    }
}