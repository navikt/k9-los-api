package no.nav.k9.los.nyoppgavestyring.query.mapping

import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter

object OppgavefilterOperatorKorrigerer {
    fun korriger(filtere: List<Oppgavefilter>): List<Oppgavefilter> {
        return filtere
            .map { oppgavefilter ->
                when (oppgavefilter) {
                    is FeltverdiOppgavefilter ->
                        byttOperator(oppgavefilter)


                    is CombineOppgavefilter -> CombineOppgavefilter(
                        oppgavefilter.combineOperator, korriger(oppgavefilter.filtere)
                    )
                }
            }
    }

    private fun byttOperator(oppgavefilter: FeltverdiOppgavefilter): Oppgavefilter {
        val operator = EksternFeltverdiOperator.valueOf(oppgavefilter.operator)
        return when {
            operator == EksternFeltverdiOperator.EQUALS && oppgavefilter.verdi.size > 1 ->
                oppgavefilter.copy(
                    operator = EksternFeltverdiOperator.IN.name
                )

            operator == EksternFeltverdiOperator.IN && oppgavefilter.verdi.size == 1 ->
                oppgavefilter.copy(
                    operator = EksternFeltverdiOperator.EQUALS.name
                )

            operator == EksternFeltverdiOperator.NOT_EQUALS && oppgavefilter.verdi.size > 1 ->
                oppgavefilter.copy(
                    operator = EksternFeltverdiOperator.NOT_IN.name
                )

            operator == EksternFeltverdiOperator.NOT_IN && oppgavefilter.verdi.size == 1 ->
                oppgavefilter.copy(
                    operator = EksternFeltverdiOperator.NOT_EQUALS.name
                )

            listOf(
                EksternFeltverdiOperator.GREATER_THAN_OR_EQUALS,
                EksternFeltverdiOperator.GREATER_THAN,
                EksternFeltverdiOperator.LESS_THAN,
                EksternFeltverdiOperator.LESS_THAN_OR_EQUALS
            ).contains(operator) && oppgavefilter.verdi.size > 1 ->
                throw IllegalStateException("Ugyldig operator for mengde")

            else -> oppgavefilter
        }
    }

}
