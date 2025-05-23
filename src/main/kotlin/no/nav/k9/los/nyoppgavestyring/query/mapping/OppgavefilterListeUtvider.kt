package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter

object OppgavefilterListeUtvider {
    fun utvid(oppgavefiltere: List<Oppgavefilter>): List<Oppgavefilter> {
        return oppgavefiltere.map { filter ->
            when (filter) {
                is FeltverdiOppgavefilter -> utvid(filter)
                is CombineOppgavefilter -> CombineOppgavefilter(
                    combineOperator = filter.combineOperator,
                    filtere = utvid(filter.filtere)
                )
            }
        }
    }

    fun utvid(filter: FeltverdiOppgavefilter): Oppgavefilter {
        if (filter.verdi.size <= 1) {
            return filter
        }

        val operator = filter.operator
        if (operator == EksternFeltverdiOperator.INTERVAL) {
            return lagInterval(filter)
        }

        val (combineOperator, feltverdiOperator) = when (operator) {
            EksternFeltverdiOperator.IN -> (CombineOperator.OR to FeltverdiOperator.EQUALS)
            EksternFeltverdiOperator.NOT_IN, EksternFeltverdiOperator.NOT_EQUALS -> (CombineOperator.AND to FeltverdiOperator.NOT_EQUALS)
            EksternFeltverdiOperator.EQUALS -> (CombineOperator.AND to FeltverdiOperator.EQUALS)
            else -> throw IllegalStateException("Ukjent feltverdioperator for mengder")
        }

        return CombineOppgavefilter(
            combineOperator = combineOperator,
            filtere = filter.verdi.map { verdi ->
                filter.copy(
                    operator = feltverdiOperator.tilEksternFeltverdiOperator(),
                    verdi = listOf(verdi)
                )
            }
        )
    }

    // Forventer at listen er sortert når intervall er brukt
    private fun lagInterval(dto: FeltverdiOppgavefilter): CombineOppgavefilter {
        return CombineOppgavefilter(
            combineOperator = CombineOperator.AND,
            filtere = listOf(
                dto.copy(
                    operator = EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                    verdi = listOf(dto.verdi.first()),
                ),
                dto.copy(
                    operator = EksternFeltverdiOperator.LESS_THAN_OR_EQUALS,
                    verdi = listOf(dto.verdi.last()),
                )
            )
        )
    }
}