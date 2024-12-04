package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter

object OppgavefilterUtenBetingelserFjerner {
    fun fjern(oppgavefiltere: List<Oppgavefilter>): List<Oppgavefilter> {
        return oppgavefiltere
            .map { oppgavefilter ->
                when (oppgavefilter) {
                    is FeltverdiOppgavefilter -> oppgavefilter
                    is CombineOppgavefilter -> CombineOppgavefilter(
                        oppgavefilter.combineOperator, fjern(oppgavefilter.filtere)
                    )
                }
            }
            .filter { oppgavefilter ->
                when (oppgavefilter) {
                    is FeltverdiOppgavefilter -> oppgavefilter.verdi.isNotEmpty()
                    is CombineOppgavefilter -> oppgavefilter.filtere.isNotEmpty()
                }
            }
    }

}
