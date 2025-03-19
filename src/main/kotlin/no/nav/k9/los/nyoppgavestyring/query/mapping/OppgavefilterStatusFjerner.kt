package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter

object OppgavefilterStatusFjerner {
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
                    is FeltverdiOppgavefilter -> oppgavefilter.kode != "oppgavestatus"
                    is CombineOppgavefilter -> oppgavefilter.filtere.isNotEmpty()
                }
            }
    }
}
