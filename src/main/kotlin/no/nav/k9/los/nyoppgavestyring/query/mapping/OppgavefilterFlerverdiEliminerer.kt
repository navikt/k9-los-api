package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter

object OppgavefilterFlerverdiEliminerer {
    fun eliminer(oppgavefiltere: List<Oppgavefilter>): List<Oppgavefilter> {
        return oppgavefiltere.map { filter ->
            when (filter) {
                is FeltverdiOppgavefilter -> map(filter)
                is CombineOppgavefilter -> CombineOppgavefilter(
                    combineOperator = filter.combineOperator,
                    filtere = eliminer(filter.filtere)
                )
            }
        }
    }

    private fun map(filter: FeltverdiOppgavefilter): Oppgavefilter {
        if (filter.verdi.size <= 1 || filter.operator == EksternFeltverdiOperator.IN.name || filter.operator == EksternFeltverdiOperator.NOT_IN.name) {
            return filter
        }

        if (filter.operator == EksternFeltverdiOperator.INTERVAL.name) {
            return lagInterval(filter)
        }

        return CombineOppgavefilter(
            combineOperator = CombineOperator.AND.name,
            filtere = filter.verdi.map { verdi ->
                filter.copy(
                    verdi = listOf(verdi)
                )
            }
        )
    }

    // Forventer at listen er sortert når intervall er brukt
    private fun lagInterval(dto: FeltverdiOppgavefilter): CombineOppgavefilter {
        return CombineOppgavefilter(
            combineOperator = CombineOperator.AND.name,
            filtere = listOf(
                dto.copy(
                    operator = EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS.name,
                    verdi = listOf(dto.verdi.first()),
                ),
                dto.copy(
                    operator = EksternFeltverdiOperator.LESS_THAN_OR_EQUALS.name,
                    verdi = listOf(dto.verdi.last()),
                )
            )
        )
    }
}