package no.nav.k9.los.nyoppgavestyring.query.db

import no.nav.k9.los.nyoppgavestyring.query.dto.query.CombineOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.FeltverdiOppgavefilter
import no.nav.k9.los.nyoppgavestyring.query.dto.query.Oppgavefilter

object OppgavefilterOperatorUtvider {
    fun utvid(oppgavefiltere: List<Oppgavefilter>): List<Oppgavefilter> {
        return oppgavefiltere.utvidListe()
    }

    private fun List<Oppgavefilter>.utvidListe(): List<Oppgavefilter> {
        return map { filter ->
            when (filter) {
                is FeltverdiOppgavefilter -> map(filter)
                is CombineOppgavefilter -> CombineOppgavefilter(
                    combineOperator = filter.combineOperator,
                    filter.filtere.utvidListe()
                )
                else -> throw IllegalStateException("Ukjent filter: " + filter::class.qualifiedName)
            }
        }
    }

    private fun map(dto: FeltverdiOppgavefilter): Oppgavefilter {
        if (dto.verdi.size <= 1) {
            return dto
        }

        val operators = when (FeltverdiOperator.valueOf(dto.operator)) {
            FeltverdiOperator.IN -> (CombineOperator.OR to FeltverdiOperator.EQUALS)
            FeltverdiOperator.NOT_IN, FeltverdiOperator.NOT_EQUALS -> (CombineOperator.AND to FeltverdiOperator.NOT_EQUALS)
            FeltverdiOperator.EQUALS -> (CombineOperator.AND to FeltverdiOperator.EQUALS)
            else -> throw IllegalStateException("Ukjent feltverdiOperator for mengeoperasjoner")
        }

        return CombineOppgavefilter(
            combineOperator = operators.first.kode,
            filtere = dto.verdi.map { verdi ->
                dto.copy(
                    operator = operators.second.name,
                    verdi = listOf(verdi)
                )
            }
        )
    }
}