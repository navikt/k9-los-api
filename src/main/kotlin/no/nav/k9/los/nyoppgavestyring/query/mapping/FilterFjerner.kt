package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter

object FilterFjerner {
    fun fjern(oppgavefiltere: List<Oppgavefilter>, filterkode: String): List<Oppgavefilter> {
        return oppgavefiltere
            .map { oppgavefilter ->
                when (oppgavefilter) {
                    is FeltverdiOppgavefilter -> oppgavefilter
                    is CombineOppgavefilter -> CombineOppgavefilter(
                        oppgavefilter.combineOperator, fjern(oppgavefilter.filtere, filterkode)
                    )
                }
            }
            .filter { oppgavefilter ->
                when (oppgavefilter) {
                    is FeltverdiOppgavefilter -> oppgavefilter.kode != filterkode
                    is CombineOppgavefilter -> oppgavefilter.filtere.isNotEmpty()
                }
            }
    }
}
