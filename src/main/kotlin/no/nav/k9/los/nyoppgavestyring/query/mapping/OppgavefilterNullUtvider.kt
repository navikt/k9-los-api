package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter

object OppgavefilterNullUtvider {
    fun utvid(oppgavefiltere: List<Oppgavefilter>): List<Oppgavefilter> {
        return oppgavefiltere.map { filter ->
            when (filter) {
                is FeltverdiOppgavefilter -> mapNotNull(filter)
                is CombineOppgavefilter -> CombineOppgavefilter(
                    combineOperator = filter.combineOperator,
                    filtere = utvid(filter.filtere)
                )
            }
        }
    }

    private fun mapNotNull(filter: FeltverdiOppgavefilter): Oppgavefilter {
        if (filter.verdi.any { it == null }) {
            return OppgavefilterListeUtvider.eliminer(filter)
        }
        return filter
    }

}
