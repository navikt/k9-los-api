package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter

object OppgavefilterListeEliminerer {
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
        if (filter.verdi.size <= 1) {
            return filter
        }

        val operator = EksternFeltverdiOperator.valueOf(filter.operator)
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
            combineOperator = combineOperator.kode,
            filtere = filter.verdi.map { verdi ->
                filter.copy(
                    operator = feltverdiOperator.name,
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