package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter

object OppgavefilterRens {
    fun rens(oppgavefiltere: List<Oppgavefilter>): List<Oppgavefilter> {
        return oppgavefiltere
//            .let { OppgavefilterUtenBetingelserFjerner.fjern(it)}
            .let { OppgavefilterOperatorUtvider.utvid(it) }
            .let { OppgavefilterDatoTypeUtvider.utvid(it) }
    }
}